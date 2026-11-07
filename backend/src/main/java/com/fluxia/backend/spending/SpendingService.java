package com.fluxia.backend.spending;

import com.fluxia.backend.category.Category;
import com.fluxia.backend.category.CategoryKind;
import com.fluxia.backend.category.CategoryRepository;
import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.transaction.CategoryTotal;
import com.fluxia.backend.transaction.TransactionDtos;
import com.fluxia.backend.transaction.TransactionRepository;
import com.fluxia.backend.transaction.TransactionService;
import com.fluxia.backend.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tope diario, modo salida y la tarjeta simulada que los hace cumplir.
 *
 * <p>La tarjeta es una demostración: en la vida real, solo quien emite la
 * tarjeta puede rechazar una compra. Pero la regla es la misma que usaría un
 * emisor, que le pregunta al servidor por cada compra y espera un sí o un no.
 *
 * <p>Tres decisiones que conviene poder sustentar:
 *
 * <ul>
 *   <li><b>Decide una regla fija, no la IA.</b> La IA propone el tope, pero
 *       aprobar o rechazar una compra lo decide {@link #decide}: un emisor
 *       real da unos dos segundos para responder, y un modelo puede tardar
 *       más, fallar o inventar.
 *   <li><b>El modo salida no se apaga antes de tiempo.</b> No hay forma de
 *       desactivarlo hasta las 8:00 del día siguiente. Si se pudiera quitar
 *       con un toque, se quitaría justo cuando hace falta.
 *   <li><b>Transporte y salud pasan siempre.</b> Bloquear todo a las 3 a. m.
 *       podría dejar a alguien sin cómo volver a su casa.
 * </ul>
 *
 * <p>Registrar un movimiento a mano nunca se bloquea: si el gasto ya
 * ocurrió, la cifra tiene que quedar.
 */
@Service
public class SpendingService {

    /** El modo salida dura hasta esta hora de la mañana siguiente. */
    static final LocalTime GOING_OUT_ENDS_AT = LocalTime.of(8, 0);

    /** Categorías del sistema que la tarjeta aprueba siempre. */
    static final List<String> ALWAYS_ALLOWED = List.of("transporte", "salud");

    /** Días que se miran para proponer un tope. */
    static final int PROPOSAL_DAYS = 30;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SpendingSettingsRepository settingsRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final FluxProperties properties;
    private final Clock clock;

    public SpendingService(SpendingSettingsRepository settingsRepository,
                           TransactionRepository transactionRepository,
                           CategoryRepository categoryRepository,
                           TransactionService transactionService,
                           FluxProperties properties,
                           Clock clock) {
        this.settingsRepository = settingsRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.properties = properties;
        this.clock = clock;
    }

    // -----------------------------------------------------------------
    // Lectura
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public SpendingDtos.TodayResponse today(UUID userId) {
        SpendingSettings settings = settingsRepository.findWithExclusionsByUserId(userId).orElse(null);
        return buildToday(userId, settings, OffsetDateTime.now(clock));
    }

    /**
     * El gasto promedio por día de las últimas semanas, sin los gastos fijos.
     * Es la base sobre la que la IA propone el tope.
     *
     * <p>Si la cuenta es más nueva que el período, se divide por los días que
     * tiene: diez días de gastos repartidos en treinta darían un promedio
     * falsamente bajo.
     *
     * @param excludedOrNull las categorías que no cuentan; null para usar las
     *                       que el usuario ya guardó
     */
    @Transactional(readOnly = true)
    public DailyAverage dailyAverage(UUID userId, Collection<UUID> excludedOrNull) {
        Set<UUID> excluded = excludedOrNull != null
                ? new HashSet<>(excludedOrNull)
                : savedExclusions(userId);
        ZoneId zone = properties.getTimezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<String> excludedNames = categoryRepository.findVisibleTo(userId).stream()
                .filter(category -> excluded.contains(category.getId()))
                .map(Category::getName)
                .toList();

        OffsetDateTime first = transactionRepository.firstOccurredAt(userId);
        if (first == null) {
            return new DailyAverage(0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), excludedNames);
        }
        long sinceFirst = ChronoUnit.DAYS.between(first.atZoneSameInstant(zone).toLocalDate(), today);
        int days = (int) Math.clamp(sinceFirst, 1, PROPOSAL_DAYS);

        // Hasta el inicio de hoy: el día en curso está incompleto y bajaría
        // el promedio.
        OffsetDateTime from = today.minusDays(days).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = today.atStartOfDay(zone).toOffsetDateTime();

        BigDecimal total = transactionRepository.sumExpensesBetween(userId, from, to, excluded);
        List<CategoryTotal> byCategory = transactionRepository
                .sumByCategoryBetween(userId, TransactionType.EXPENSE, from, to).stream()
                .filter(row -> !excluded.contains(row.categoryId()))
                .toList();

        return new DailyAverage(
                days,
                total,
                total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP),
                byCategory,
                excludedNames);
    }

    // -----------------------------------------------------------------
    // Escritura
    // -----------------------------------------------------------------

    /** Reemplaza el tope y los gastos fijos. Un tope null lo quita. */
    @Transactional
    public SpendingDtos.TodayResponse updateSettings(UUID userId, SpendingDtos.SettingsRequest request) {
        Map<UUID, Category> visible = categoryRepository.findVisibleTo(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Set<UUID> excluded = new LinkedHashSet<>(request.excludedCategoryIds());
        for (UUID id : excluded) {
            Category category = visible.get(id);
            if (category == null) {
                throw ApiException.notFound("La categoría no existe.");
            }
            if (category.getKind() != CategoryKind.EXPENSE) {
                throw ApiException.badRequest("CATEGORY_KIND_MISMATCH",
                        "Solo las categorías de gasto pueden quedar fuera del tope.");
            }
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        SpendingSettings settings = settingsRepository.findWithExclusionsByUserId(userId)
                .orElseGet(() -> new SpendingSettings(userId));
        settings.setDailyLimit(request.dailyLimit());
        settings.setExcludedCategoryIds(excluded);
        settings.touch(now);
        return buildToday(userId, settingsRepository.save(settings), now);
    }

    /**
     * Activa el modo salida hasta las 8:00. Si ya estaba activo, no cambia la
     * hora: tocar de nuevo no lo alarga ni lo acorta.
     */
    @Transactional
    public SpendingDtos.TodayResponse startGoingOut(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        SpendingSettings settings = settingsRepository.findForUpdate(userId)
                .orElseGet(() -> new SpendingSettings(userId));
        if (!settings.isGoingOut(now)) {
            settings.setGoingOutUntil(goingOutEnd(now.atZoneSameInstant(properties.getTimezone())));
        }
        settings.touch(now);
        return buildToday(userId, settingsRepository.save(settings), now);
    }

    /**
     * Una compra con la tarjeta simulada. Si se aprueba, queda como un gasto
     * más, por el mismo camino que uno escrito a mano; si se rechaza, no se
     * guarda nada.
     */
    @Transactional
    public SpendingDtos.PurchaseResponse purchase(UUID userId, SpendingDtos.PurchaseRequest request) {
        Category category = categoryRepository.findByIdVisibleTo(request.categoryId(), userId)
                .orElseThrow(() -> ApiException.notFound("La categoría no existe."));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw ApiException.badRequest("CATEGORY_KIND_MISMATCH",
                    "Con la tarjeta solo se pagan gastos: elige una categoría de gasto.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        SpendingSettings settings = settingsRepository.findForUpdate(userId).orElse(null);
        boolean essential = category.isSystemCategory() && ALWAYS_ALLOWED.contains(category.getSlug());
        boolean goingOut = settings != null && settings.isGoingOut(now);
        BigDecimal limit = settings != null ? settings.getDailyLimit() : null;
        boolean counts = settings == null || !settings.getExcludedCategoryIds().contains(category.getId());

        BigDecimal spent = BigDecimal.ZERO;
        if (limit != null && counts && !essential && !goingOut) {
            Day day = dayOf(now);
            spent = transactionRepository.sumExpensesBetween(userId, day.from(), day.to(), settings.getExcludedCategoryIds());
        }

        SpendingDtos.Decision decision = decide(essential, goingOut, limit, counts, spent, request.amount());
        TransactionDtos.Response created = null;
        if (decision == SpendingDtos.Decision.APPROVED) {
            created = transactionService.create(userId, new TransactionDtos.CreateRequest(
                    request.description(), request.amount(), TransactionType.EXPENSE,
                    category.getId(), null, now));
        }

        SpendingDtos.TodayResponse today = buildToday(userId, settings, now);
        return new SpendingDtos.PurchaseResponse(
                created != null, decision, message(decision, today), created, today);
    }

    // -----------------------------------------------------------------
    // Reglas
    // -----------------------------------------------------------------

    /**
     * La regla de la tarjeta, sin base de datos para poder probarla sola.
     *
     * <p>El orden importa: lo esencial pasa primero, aun con el modo salida y
     * aunque no quede tope; el modo salida rechaza todo lo demás; y el tope
     * solo mira las compras que cuentan para él.
     */
    static SpendingDtos.Decision decide(boolean essential,
                                        boolean goingOut,
                                        BigDecimal limit,
                                        boolean countsForLimit,
                                        BigDecimal spentToday,
                                        BigDecimal amount) {
        if (essential) {
            return SpendingDtos.Decision.APPROVED;
        }
        if (goingOut) {
            return SpendingDtos.Decision.DECLINED_GOING_OUT;
        }
        if (limit != null && countsForLimit && spentToday.add(amount).compareTo(limit) > 0) {
            return SpendingDtos.Decision.DECLINED_DAILY_LIMIT;
        }
        return SpendingDtos.Decision.APPROVED;
    }

    /**
     * Las 8:00 siguientes: las de hoy si todavía no llegaron (quien lo activa
     * a la 1:00 ya está afuera), las de mañana si ya pasaron.
     */
    static OffsetDateTime goingOutEnd(ZonedDateTime now) {
        LocalDate day = now.toLocalTime().isBefore(GOING_OUT_ENDS_AT)
                ? now.toLocalDate()
                : now.toLocalDate().plusDays(1);
        return day.atTime(GOING_OUT_ENDS_AT).atZone(now.getZone()).toOffsetDateTime();
    }

    // -----------------------------------------------------------------
    // Internos
    // -----------------------------------------------------------------

    private SpendingDtos.TodayResponse buildToday(UUID userId, SpendingSettings settings, OffsetDateTime now) {
        Day day = dayOf(now);
        Set<UUID> excluded = settings != null ? settings.getExcludedCategoryIds() : Set.of();
        BigDecimal spent = transactionRepository.sumExpensesBetween(userId, day.from(), day.to(), excluded);
        BigDecimal limit = settings != null ? settings.getDailyLimit() : null;

        BigDecimal remaining = null;
        BigDecimal overBy = null;
        BigDecimal progress = null;
        if (limit != null) {
            remaining = limit.subtract(spent).max(BigDecimal.ZERO);
            overBy = spent.subtract(limit).max(BigDecimal.ZERO);
            progress = spent.multiply(HUNDRED)
                    .divide(limit, 2, RoundingMode.HALF_UP)
                    .min(HUNDRED);
        }

        boolean goingOut = settings != null && settings.isGoingOut(now);
        List<Category> shown = categoryRepository.findByIdsOrSystemSlugs(
                userId,
                excluded.isEmpty() ? List.of(TransactionRepository.NO_CATEGORY) : excluded,
                ALWAYS_ALLOWED);

        return new SpendingDtos.TodayResponse(
                day.from(),
                day.to(),
                limit,
                spent,
                remaining,
                overBy,
                progress,
                new SpendingDtos.GoingOut(goingOut, goingOut ? settings.getGoingOutUntil() : null),
                shown.stream().filter(c -> excluded.contains(c.getId())).map(SpendingService::ref).toList(),
                shown.stream().filter(c -> c.isSystemCategory() && ALWAYS_ALLOWED.contains(c.getSlug()))
                        .map(SpendingService::ref).toList());
    }

    private Set<UUID> savedExclusions(UUID userId) {
        return settingsRepository.findWithExclusionsByUserId(userId)
                .map(settings -> new HashSet<>(settings.getExcludedCategoryIds()))
                .orElseGet(HashSet::new);
    }

    /** El día en curso en la zona de la app, como rango semiabierto. */
    private Day dayOf(OffsetDateTime now) {
        ZoneId zone = properties.getTimezone();
        LocalDate date = now.atZoneSameInstant(zone).toLocalDate();
        return new Day(
                date.atStartOfDay(zone).toOffsetDateTime(),
                date.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }

    private static String message(SpendingDtos.Decision decision, SpendingDtos.TodayResponse today) {
        return switch (decision) {
            case APPROVED -> "Ya quedó en tus movimientos.";
            case DECLINED_GOING_OUT -> "Tienes el modo salida activo: solo pasan "
                    + names(today.alwaysAllowedCategories()) + ".";
            case DECLINED_DAILY_LIMIT -> "Con esta compra te pasarías del tope de hoy.";
        };
    }

    /** «Transporte y Salud». */
    private static String names(List<TransactionDtos.CategoryRef> categories) {
        List<String> names = categories.stream().map(TransactionDtos.CategoryRef::name).toList();
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.getLast();
    }

    private static TransactionDtos.CategoryRef ref(Category category) {
        return new TransactionDtos.CategoryRef(
                category.getId(), category.getSlug(), category.getName(), category.getIcon(), category.getColor());
    }

    private record Day(OffsetDateTime from, OffsetDateTime to) {
    }
}

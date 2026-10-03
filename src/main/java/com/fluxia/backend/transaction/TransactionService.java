package com.fluxia.backend.transaction;

import com.fluxia.backend.account.Account;
import com.fluxia.backend.account.AccountRepository;
import com.fluxia.backend.category.Category;
import com.fluxia.backend.category.CategoryKind;
import com.fluxia.backend.category.CategoryRepository;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.user.User;
import com.fluxia.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reglas de negocio de los movimientos.
 *
 * <p>Dos invariantes que este servicio sostiene y que el prototipo no
 * tenia:
 *
 * <ul>
 *   <li><b>Aislamiento por usuario.</b> Toda consulta arranca con
 *       {@code ownedBy(userId)} y toda escritura verifica que el
 *       recurso sea del usuario. Un id ajeno responde 404, nunca los
 *       datos de otra persona.
 *   <li><b>Coherencia de categoria.</b> Un gasto no puede clasificarse
 *       en una categoria de ingresos. Es la validacion que faltaba para
 *       que los totales de las pantallas cuadren entre si.
 * </ul>
 */
@Service
public class TransactionService {

    /** Tope de elementos por pagina, para que nadie pida 10 000 de una vez. */
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // -----------------------------------------------------------------
    // Lectura
    // -----------------------------------------------------------------

    /**
     * Listado filtrado, paginado y con los totales del filtro completo.
     *
     * <p>Los totales se calculan sobre todo el filtro, no sobre la
     * pagina: el cliente puede mostrar "Comida: $4.570" aunque solo
     * haya descargado los primeros 20 movimientos.
     */
    @Transactional(readOnly = true)
    public TransactionDtos.PageResponse list(UUID userId,
                                             OffsetDateTime from,
                                             OffsetDateTime to,
                                             String categorySlug,
                                             TransactionType type,
                                             String search,
                                             int page,
                                             int size) {

        Specification<Transaction> spec = TransactionSpecifications.ownedBy(userId);

        if (from != null) {
            spec = spec.and(TransactionSpecifications.occurredFrom(from));
        }
        if (to != null) {
            spec = spec.and(TransactionSpecifications.occurredBefore(to));
        }
        if (type != null) {
            spec = spec.and(TransactionSpecifications.ofType(type));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(TransactionSpecifications.descriptionContains(search.trim()));
        }
        if (categorySlug != null && !categorySlug.isBlank() && !"todos".equals(categorySlug)) {
            // "todos" es el chip por defecto del prototipo: no filtra.
            Category category = resolveCategoryBySlug(categorySlug.trim(), userId);
            spec = spec.and(TransactionSpecifications.inCategory(category.getId()));
        }

        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                safeSize,
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        Page<Transaction> found = transactionRepository.findAll(spec, pageRequest);

        // Mismo Specification, ahora para sumar.
        Map<TransactionType, BigDecimal> sums = transactionRepository.sumByType(spec);

        return new TransactionDtos.PageResponse(
                found.getContent().stream().map(TransactionDtos.Response::from).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages(),
                buildTotals(sums));
    }

    @Transactional(readOnly = true)
    public TransactionDtos.Response getById(UUID userId, UUID transactionId) {
        return TransactionDtos.Response.from(requireOwned(userId, transactionId));
    }

    /** Los ultimos movimientos, para la tarjeta de actividad reciente. */
    @Transactional(readOnly = true)
    public List<TransactionDtos.Response> recent(UUID userId, int limit) {
        int safeLimit = Math.clamp(limit, 1, 50);
        return transactionRepository
                .findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(TransactionDtos.Response::from)
                .toList();
    }

    // -----------------------------------------------------------------
    // Escritura
    // -----------------------------------------------------------------

    @Transactional
    public TransactionDtos.Response create(UUID userId, TransactionDtos.CreateRequest request) {
        User user = userRepository.getReferenceById(userId);
        Category category = requireVisibleCategory(request.categoryId(), userId);
        Account account = resolveAccount(request.accountId(), userId);

        requireKindMatchesType(category, request.type());

        Transaction transaction = new Transaction(
                user,
                account,
                category,
                request.description().trim(),
                request.amount(),
                request.type(),
                request.occurredAt());

        return TransactionDtos.Response.from(transactionRepository.save(transaction));
    }

    /**
     * Modificacion parcial: solo se toca lo que viene en la peticion.
     *
     * <p>Al cambiar tipo o categoria se vuelve a validar la coherencia
     * entre ambos, porque cualquiera de los dos cambios por separado
     * puede dejar el movimiento mal clasificado.
     */
    @Transactional
    public TransactionDtos.Response update(UUID userId,
                                           UUID transactionId,
                                           TransactionDtos.UpdateRequest request) {

        Transaction transaction = requireOwned(userId, transactionId);

        if (request.description() != null && !request.description().isBlank()) {
            transaction.setDescription(request.description().trim());
        }
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.type() != null) {
            transaction.setType(request.type());
        }
        if (request.categoryId() != null) {
            transaction.setCategory(requireVisibleCategory(request.categoryId(), userId));
        }
        if (request.accountId() != null) {
            transaction.setAccount(resolveAccount(request.accountId(), userId));
        }
        if (request.occurredAt() != null) {
            transaction.setOccurredAt(request.occurredAt());
        }

        requireKindMatchesType(transaction.getCategory(), transaction.getType());

        return TransactionDtos.Response.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        transactionRepository.delete(requireOwned(userId, transactionId));
    }

    // -----------------------------------------------------------------
    // Internos
    // -----------------------------------------------------------------

    /**
     * Carga un movimiento exigiendo que sea del usuario.
     *
     * <p>Devuelve 404 y no 403 a proposito: un 403 confirmaria que ese
     * movimiento existe y es de alguien mas.
     */
    private Transaction requireOwned(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> ApiException.notFound("El movimiento no existe."));
    }

    private Category requireVisibleCategory(UUID categoryId, UUID userId) {
        return categoryRepository.findByIdVisibleTo(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("La categoria no existe."));
    }

    private Category resolveCategoryBySlug(String slug, UUID userId) {
        List<Category> matches = categoryRepository.findBySlugVisibleTo(slug, userId);
        if (matches.isEmpty()) {
            throw ApiException.notFound("No existe la categoria '" + slug + "'.");
        }
        // La consulta ordena la propia del usuario antes que la del sistema.
        return matches.get(0);
    }

    /** Si no se indica cuenta, se usa la primera que tenga el usuario. */
    private Account resolveAccount(UUID accountId, UUID userId) {
        if (accountId != null) {
            return accountRepository.findByIdAndUserId(accountId, userId)
                    .orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
        }
        return accountRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseThrow(() -> ApiException.badRequest("NO_ACCOUNT",
                        "No tienes ninguna cuenta creada."));
    }

    /** Impide clasificar un gasto en una categoria de ingresos, y al reves. */
    private void requireKindMatchesType(Category category, TransactionType type) {
        boolean coherent =
                (type == TransactionType.INCOME && category.getKind() == CategoryKind.INCOME)
                        || (type == TransactionType.EXPENSE && category.getKind() == CategoryKind.EXPENSE);

        if (!coherent) {
            throw ApiException.badRequest("CATEGORY_KIND_MISMATCH",
                    "La categoria '" + category.getName() + "' es de tipo "
                            + category.getKind() + " y no admite un movimiento de tipo "
                            + type + ".");
        }
    }

    private TransactionDtos.Totals buildTotals(Map<TransactionType, BigDecimal> sums) {
        BigDecimal income = sums.getOrDefault(TransactionType.INCOME, BigDecimal.ZERO);
        BigDecimal expense = sums.getOrDefault(TransactionType.EXPENSE, BigDecimal.ZERO);
        return new TransactionDtos.Totals(income, expense, income.subtract(expense));
    }
}

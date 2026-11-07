package com.fluxia.backend.spending;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * El tope diario y el modo salida de un usuario: una fila por usuario, que
 * existe solo cuando configuró alguno de los dos.
 *
 * <p>Guarda decisiones del usuario, no resultados: lo gastado en el día se
 * suma desde {@code transactions} en cada consulta. Si se guardara aquí,
 * borrar o editar un movimiento lo dejaría desactualizado.
 *
 * <p>Las categorías excluidas son ids sueltos y no una relación con
 * {@code Category}: solo se usan para filtrar la suma, y así cargar los
 * ajustes no arrastra las categorías.
 */
@Entity
@Table(name = "spending_settings")
public class SpendingSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Null: sin tope. */
    @Column(name = "daily_limit", precision = 14, scale = 2)
    private BigDecimal dailyLimit;

    /** Null o pasado: el modo salida está apagado. */
    @Column(name = "going_out_until")
    private OffsetDateTime goingOutUntil;

    @ElementCollection
    @CollectionTable(name = "spending_excluded_categories", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "category_id", nullable = false)
    private Set<UUID> excludedCategoryIds = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SpendingSettings() {
        // Constructor exigido por JPA.
    }

    public SpendingSettings(UUID userId) {
        this.userId = userId;
    }

    /** Si el modo salida sigue vigente en ese instante. */
    public boolean isGoingOut(OffsetDateTime now) {
        return goingOutUntil != null && goingOutUntil.isAfter(now);
    }

    /**
     * Se llama a mano en cada cambio: si solo cambian las categorías
     * excluidas, Hibernate no considera modificada la fila y un
     * {@code @PreUpdate} no se ejecutaría.
     */
    public void touch(OffsetDateTime now) {
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public OffsetDateTime getGoingOutUntil() {
        return goingOutUntil;
    }

    public void setGoingOutUntil(OffsetDateTime goingOutUntil) {
        this.goingOutUntil = goingOutUntil;
    }

    public Set<UUID> getExcludedCategoryIds() {
        return excludedCategoryIds;
    }

    public void setExcludedCategoryIds(Set<UUID> excludedCategoryIds) {
        this.excludedCategoryIds.clear();
        this.excludedCategoryIds.addAll(excludedCategoryIds);
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

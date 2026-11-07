package com.fluxia.backend.spending;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpendingSettingsRepository extends JpaRepository<SpendingSettings, UUID> {

    /**
     * Los ajustes con sus categorías excluidas en la misma consulta: cada
     * ida a la base cuesta unos 150 ms, y la tarjeta de Inicio los pide
     * siempre juntos.
     */
    @EntityGraph(attributePaths = "excludedCategoryIds")
    Optional<SpendingSettings> findWithExclusionsByUserId(UUID userId);

    /**
     * Los mismos ajustes, bloqueando la fila hasta el fin de la
     * transacción. Lo usa la tarjeta simulada: dos compras al mismo tiempo
     * no pueden pasar las dos con el saldo que solo alcanzaba para una.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SpendingSettings s WHERE s.userId = :userId")
    Optional<SpendingSettings> findForUpdate(@Param("userId") UUID userId);
}

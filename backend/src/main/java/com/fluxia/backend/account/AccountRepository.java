package com.fluxia.backend.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByNameAsc(UUID userId);

    /**
     * Siempre se busca por id MAS userId. Es la defensa contra que un
     * usuario pida el id de una cuenta ajena: si no es suya, devuelve
     * vacio y el servicio responde 404.
     */
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    Optional<Account> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);
}

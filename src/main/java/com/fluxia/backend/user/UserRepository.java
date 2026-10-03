package com.fluxia.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca por correo ignorando mayusculas, igual que el indice unico
     * {@code ux_users_email_lower} de la migracion V1. Si aqui se
     * comparara sensible a mayusculas, se podrian registrar dos cuentas
     * que la base de datos considera la misma.
     */
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoringCase(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoringCase(@Param("email") String email);
}

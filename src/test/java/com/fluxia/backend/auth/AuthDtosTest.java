package com.fluxia.backend.auth;

import com.fluxia.backend.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del calculo de iniciales para el avatar.
 *
 * <p>El prototipo tenia "DF" escrito a mano en el HTML. Al venir de un
 * usuario real hay que cubrir los casos raros: un solo nombre, nombres
 * compuestos, espacios de mas.
 */
class AuthDtosTest {

    @Test
    @DisplayName("Nombre y apellido dan las dos iniciales, como el 'DF' del prototipo")
    void firstAndLastInitial() {
        assertThat(initialsOf("Diego Fernández")).isEqualTo("DF");
    }

    @Test
    @DisplayName("Con un solo nombre se usa una sola inicial")
    void singleName() {
        assertThat(initialsOf("Diego")).isEqualTo("D");
    }

    @Test
    @DisplayName("Con varios nombres se toman el primero y el ultimo")
    void firstAndLastOfSeveralNames() {
        assertThat(initialsOf("Ana María López Díaz")).isEqualTo("AD");
    }

    @Test
    @DisplayName("Los espacios de mas no producen iniciales vacias")
    void extraWhitespaceIsIgnored() {
        assertThat(initialsOf("  diego   fernandez  ")).isEqualTo("DF");
    }

    @Test
    @DisplayName("Las iniciales siempre van en mayuscula")
    void initialsAreUppercase() {
        assertThat(initialsOf("ana lopez")).isEqualTo("AL");
    }

    private String initialsOf(String fullName) {
        return AuthDtos.UserResponse
                .from(new User("demo@fluxia.app", "hash", fullName))
                .initials();
    }
}

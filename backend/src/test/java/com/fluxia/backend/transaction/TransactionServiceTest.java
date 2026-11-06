package com.fluxia.backend.transaction;

import com.fluxia.backend.account.AccountRepository;
import com.fluxia.backend.category.CategoryRepository;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService service;

    @Test
    @DisplayName("Un movimiento con fecha futura se rechaza con un mensaje claro")
    void rejectsFutureDate() {
        assertThatThrownBy(() -> service.create(USER_ID, request(OffsetDateTime.now().plusDays(2))))
                .isInstanceOf(ApiException.class)
                .hasMessage("La fecha no puede estar en el futuro.");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Un movimiento de antes del año 2000 se rechaza")
    void rejectsAncientDate() {
        assertThatThrownBy(() -> service.create(USER_ID, request(OffsetDateTime.parse("1900-01-01T00:00:00Z"))))
                .isInstanceOf(ApiException.class)
                .hasMessage("La fecha es demasiado antigua: debe ser del año 2000 en adelante.");
    }

    @Test
    @DisplayName("Un celular con el reloj unos minutos adelantado no se rechaza por la fecha")
    void toleratesClockSkew() {
        // Pasa la fecha y sigue hasta la categoría, que en esta prueba no existe.
        assertThatThrownBy(() -> service.create(USER_ID, request(OffsetDateTime.now().plusMinutes(30))))
                .isInstanceOf(ApiException.class)
                .hasMessage("La categoría no existe.");
    }

    @Test
    @DisplayName("Editar un movimiento tampoco admite una fecha futura")
    void rejectsFutureDateOnUpdate() {
        TransactionDtos.UpdateRequest update = new TransactionDtos.UpdateRequest(
                null, null, null, null, null, OffsetDateTime.now().plusDays(2));

        assertThatThrownBy(() -> service.update(USER_ID, UUID.randomUUID(), update))
                .isInstanceOf(ApiException.class)
                .hasMessage("La fecha no puede estar en el futuro.");
        verifyNoInteractions(transactionRepository);
    }

    private TransactionDtos.CreateRequest request(OffsetDateTime occurredAt) {
        return new TransactionDtos.CreateRequest(
                "Gasolina", new BigDecimal("20000"), TransactionType.EXPENSE,
                UUID.randomUUID(), null, occurredAt);
    }
}

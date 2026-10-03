package com.fluxia.backend.transaction;

import com.fluxia.backend.account.Account;
import com.fluxia.backend.account.AccountType;
import com.fluxia.backend.category.Category;
import com.fluxia.backend.category.CategoryKind;
import com.fluxia.backend.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la convencion de signos.
 *
 * <p>Verifican la decision central del modelo de datos: el monto se
 * guarda siempre positivo y el sentido del dinero vive en el campo
 * {@code type}. El prototipo guardaba montos con signo, y un gasto mal
 * firmado sumaba al saldo en lugar de restar.
 */
class TransactionTest {

    @Test
    @DisplayName("Un gasto resta del saldo")
    void expenseIsNegativeWhenSigned() {
        Transaction expense = sample(TransactionType.EXPENSE, "2450.00", CategoryKind.EXPENSE);

        assertThat(expense.getAmount()).isEqualByComparingTo("2450.00");
        assertThat(expense.signedAmount()).isEqualByComparingTo("-2450.00");
    }

    @Test
    @DisplayName("Un ingreso suma al saldo")
    void incomeIsPositiveWhenSigned() {
        Transaction income = sample(TransactionType.INCOME, "15000.00", CategoryKind.INCOME);

        assertThat(income.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(income.signedAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("El monto guardado nunca cambia de signo al consultarlo")
    void storedAmountStaysPositive() {
        Transaction expense = sample(TransactionType.EXPENSE, "590.00", CategoryKind.EXPENSE);

        expense.signedAmount();
        expense.signedAmount();

        // negate() devuelve un valor nuevo; no muta el original. Si
        // mutara, dos lecturas del mismo movimiento darian distinto.
        assertThat(expense.getAmount().signum()).isPositive();
    }

    @Test
    @DisplayName("Los decimales se conservan exactos, sin error de coma flotante")
    void decimalsAreExact() {
        // Con double, 0.1 + 0.2 no da 0.3. En dinero eso es inaceptable,
        // y es el motivo de usar BigDecimal y NUMERIC(14,2).
        Transaction a = sample(TransactionType.EXPENSE, "0.10", CategoryKind.EXPENSE);
        Transaction b = sample(TransactionType.EXPENSE, "0.20", CategoryKind.EXPENSE);

        BigDecimal total = a.getAmount().add(b.getAmount());

        assertThat(total).isEqualByComparingTo("0.30");
        assertThat(total.toPlainString()).isEqualTo("0.30");
    }

    private Transaction sample(TransactionType type, String amount, CategoryKind kind) {
        User user = new User("demo@fluxia.app", "hash", "Demo Usuario");
        Account account = new Account(user, "Cuenta principal", AccountType.BANK, "ARS");
        Category category = new Category(null, "prueba", "Prueba", "🧪", "#6c63ff", kind);

        return new Transaction(user, account, category, "Movimiento de prueba",
                new BigDecimal(amount), type, OffsetDateTime.now());
    }
}

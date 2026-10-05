package com.fluxia.backend.transaction;

import com.fluxia.backend.account.Account;
import com.fluxia.backend.category.Category;
import com.fluxia.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento de dinero. Es la unica fuente de verdad del sistema:
 * el saldo, los reportes y el contexto que recibe la IA se calculan
 * todos a partir de esta tabla, nunca de valores guardados aparte.
 *
 * <p>Dos decisiones que conviene poder sustentar:
 *
 * <ul>
 *   <li><b>{@code amount} siempre positivo</b> y el sentido en
 *       {@link #type}. Evita la clase de error que tenia el prototipo,
 *       donde un gasto con signo equivocado sumaba al saldo.
 *   <li><b>{@code BigDecimal}, no {@code double}</b>. En coma flotante
 *       0.1 + 0.2 no da 0.3, y en dinero eso es inaceptable: la columna
 *       es NUMERIC(14,2) y el tipo de Java debe acompanarla.
 * </ul>
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 140)
    private String description;

    /** Siempre mayor que cero; lo garantiza ck_transactions_amount. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    /** Cuando ocurrio el movimiento, no cuando se registro. */
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Transaction() {
        // Constructor exigido por JPA.
    }

    public Transaction(User user,
                       Account account,
                       Category category,
                       String description,
                       BigDecimal amount,
                       TransactionType type,
                       OffsetDateTime occurredAt) {
        this.user = user;
        this.account = account;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * El monto con signo, para cuando hay que sumar en memoria.
     * Las sumas grandes NO usan esto: se hacen en SQL (ver
     * TransactionRepository) para no traer miles de filas a Java.
     */
    public BigDecimal signedAmount() {
        return type == TransactionType.INCOME ? amount : amount.negate();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

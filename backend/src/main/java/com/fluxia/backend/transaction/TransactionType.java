package com.fluxia.backend.transaction;

/**
 * Direccion del dinero. Coincide con ck_transactions_type en la V1.
 *
 * <p>Esta es la pieza que permite guardar todos los montos positivos:
 * en lugar de confiar en el signo del numero, el sentido es explicito.
 */
public enum TransactionType {
    INCOME,
    EXPENSE
}

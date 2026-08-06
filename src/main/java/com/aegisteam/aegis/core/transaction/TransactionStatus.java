package com.aegisteam.aegis.core.transaction;

/** Mirrors the {@code status} CHECK constraint in V005. */
public enum TransactionStatus {
    PENDING,
    MATCHED,
    PARTIAL,
    UNMATCHED
}

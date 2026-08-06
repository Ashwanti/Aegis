package com.aegisteam.aegis.ingestion;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single statement extracted from a document. One file can hold several
 * (banks routinely batch days or accounts together), so parsers return a list.
 *
 * @param accountId        MT940 :25: — the account the statement covers
 * @param statementNumber  MT940 :28C: — statement/sequence number
 * @param currency         taken from the balance field; MT940 statement lines
 *                         carry no currency of their own
 */
public record NormalizedStatement(
        String accountId,
        String statementNumber,
        String currency,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<NormalizedTransaction> transactions) {

    public NormalizedStatement {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}

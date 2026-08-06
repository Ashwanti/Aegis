package com.aegisteam.aegis.ingestion;

import com.aegisteam.aegis.core.transaction.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One statement line, format-agnostic. Every parser produces these, so the
 * ingestion service never sees MT940 (or anything else) directly.
 *
 * @param externalId  stable identity for this line within its source; see
 *                    {@code Mt940Parser} for how it is derived when the bank
 *                    supplies no usable reference
 * @param amount      always positive — sign lives in {@code direction}, matching
 *                    the {@code transactions} table
 * @param txnDate     booking/entry date
 * @param valueDate   settlement date; null when the document omits it
 * @param metadata    raw source fields as a JSON object string, or null
 */
public record NormalizedTransaction(
        String externalId,
        BigDecimal amount,
        String currency,
        Direction direction,
        LocalDate txnDate,
        LocalDate valueDate,
        String description,
        String reference,
        String metadata) {}

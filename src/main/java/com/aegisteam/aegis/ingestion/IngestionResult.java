package com.aegisteam.aegis.ingestion;

/**
 * Outcome of ingesting one document.
 *
 * @param statements  statements found in the file
 * @param parsed      statement lines successfully read
 * @param inserted    rows written to {@code transactions}
 * @param duplicates  lines already present for this source, skipped
 */
public record IngestionResult(int statements, int parsed, int inserted, int duplicates) {}

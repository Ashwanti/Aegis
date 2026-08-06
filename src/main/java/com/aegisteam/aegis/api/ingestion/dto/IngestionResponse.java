package com.aegisteam.aegis.api.ingestion.dto;

import com.aegisteam.aegis.ingestion.IngestionResult;

public record IngestionResponse(
        String sourceId, String format, int statements, int parsed, int inserted, int duplicates) {

    public static IngestionResponse of(String sourceId, String format, IngestionResult result) {
        return new IngestionResponse(
                sourceId,
                format,
                result.statements(),
                result.parsed(),
                result.inserted(),
                result.duplicates());
    }
}

package com.aegisteam.aegis.ingestion;

/**
 * Raised both when the source does not exist and when it belongs to another
 * org. Callers get a 404 either way, so probing ids cannot reveal whether a
 * source exists in someone else's tenant.
 */
public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(String message) {
        super(message);
    }
}

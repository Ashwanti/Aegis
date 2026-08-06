package com.aegisteam.aegis.ingestion;

/** The document could not be read as the format its source declares. */
public class StatementParseException extends RuntimeException {

    public StatementParseException(String message) {
        super(message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

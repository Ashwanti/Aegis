package com.aegisteam.aegis.ingestion;

/** A source names a parser bean that is not deployed — a configuration fault. */
public class UnknownParserException extends RuntimeException {

    public UnknownParserException(String message) {
        super(message);
    }
}

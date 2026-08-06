package com.aegisteam.aegis.api.ingestion;

import com.aegisteam.aegis.ingestion.SourceNotFoundException;
import com.aegisteam.aegis.ingestion.StatementParseException;
import com.aegisteam.aegis.ingestion.UnknownParserException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Scoped to the ingestion controllers so it cannot collide with
 * {@code AuthExceptionHandler} — two {@code @RestControllerAdvice} beans
 * handling the same exception type would be ambiguous at runtime. Error bodies
 * intentionally match the auth handler's shape.
 */
@RestControllerAdvice(assignableTypes = StatementIngestionController.class)
public class IngestionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IngestionExceptionHandler.class);

    @ExceptionHandler(SourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSourceNotFound(SourceNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 422, not 400: the request was well formed, its contents were not. */
    @ExceptionHandler(StatementParseException.class)
    public ResponseEntity<Map<String, Object>> handleParseFailure(StatementParseException e) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    /** A misconfigured source is our fault, not the caller's. */
    @ExceptionHandler(UnknownParserException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownParser(UnknownParserException e) {
        log.error("Source is misconfigured: {}", e.getMessage());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "This source is not configured for ingestion");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(
            MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "Missing multipart field: " + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.CONTENT_TOO_LARGE, "Statement file is too large");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}

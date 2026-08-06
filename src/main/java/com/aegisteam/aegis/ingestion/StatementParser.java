package com.aegisteam.aegis.ingestion;

import java.util.List;

/**
 * Reads one document format into the normalized model. Implementations are
 * Spring beans; {@code sources.parser_bean} names which one a given source
 * uses, so adding a format means adding a bean, not editing the ingestion path.
 */
public interface StatementParser {

    /** Stable format label, e.g. {@code MT940}. Matched case-insensitively. */
    String format();

    /**
     * @throws StatementParseException if the document is not readable as this
     *     format — callers turn this into a 422, never a 500
     */
    List<NormalizedStatement> parse(String content);
}

package com.aegisteam.aegis.ingestion;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Resolves the parser a source declares in {@code sources.parser_bean}.
 *
 * <p>Lookup accepts either the Spring bean name ({@code mt940Parser}) or the
 * format label ({@code MT940}), case-insensitively, because that column is
 * populated by hand and both spellings are the obvious thing to write.
 */
@Component
public class StatementParserRegistry {

    private final Map<String, StatementParser> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public StatementParserRegistry(Map<String, StatementParser> parsers) {
        parsers.forEach((beanName, parser) -> {
            byName.put(beanName, parser);
            byName.put(parser.format(), parser);
        });
    }

    /**
     * @throws UnknownParserException when the source names a parser that is not
     *     deployed — a configuration fault, so it must not be reported as a
     *     malformed document
     */
    public StatementParser resolve(String parserBean) {
        if (parserBean == null || parserBean.isBlank()) {
            throw new UnknownParserException("Source has no parser_bean configured");
        }
        StatementParser parser = byName.get(parserBean.trim());
        if (parser == null) {
            throw new UnknownParserException(
                    "No parser named '" + parserBean + "'. Available: " + available());
        }
        return parser;
    }

    /** Distinct parsers, keyed by format — used in error messages and diagnostics. */
    public Collection<String> available() {
        Map<String, String> formats = new LinkedHashMap<>();
        byName.values().forEach(parser -> formats.put(parser.format(), parser.format()));
        return formats.keySet();
    }
}

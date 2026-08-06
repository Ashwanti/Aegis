package com.aegisteam.aegis.ingestion;

import com.aegisteam.aegis.core.entity.LegalEntity;
import com.aegisteam.aegis.core.entity.LegalEntityRepository;
import com.aegisteam.aegis.core.source.Source;
import com.aegisteam.aegis.core.source.SourceRepository;
import com.aegisteam.aegis.core.transaction.Transaction;
import com.aegisteam.aegis.core.transaction.TransactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded document into rows in {@code transactions}.
 *
 * <p>Ingestion is idempotent: parsers derive a stable {@code external_id} per
 * line, and anything already stored for that source is skipped. Re-uploading
 * the same statement — which happens routinely when a bank re-sends a day —
 * is therefore a no-op rather than a duplicate.
 */
@Service
public class StatementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StatementIngestionService.class);

    /** Keeps the {@code IN (...)} list well inside Postgres' parameter limit. */
    private static final int LOOKUP_CHUNK = 1000;

    private final SourceRepository sourceRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final TransactionRepository transactionRepository;
    private final StatementParserRegistry parserRegistry;

    public StatementIngestionService(
            SourceRepository sourceRepository,
            LegalEntityRepository legalEntityRepository,
            TransactionRepository transactionRepository,
            StatementParserRegistry parserRegistry) {
        this.sourceRepository = sourceRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.transactionRepository = transactionRepository;
        this.parserRegistry = parserRegistry;
    }

    /**
     * @param callerOrgId org of the authenticated user; the source must belong
     *     to it, otherwise one tenant could write into another's ledger
     * @throws SourceNotFoundException if the source is absent or out of tenant
     * @throws StatementParseException if the document is malformed
     * @throws UnknownParserException if the source names an undeployed parser
     */
    @Transactional
    public IngestionResult ingest(UUID sourceId, UUID callerOrgId, String content) {
        Source source = sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("No source " + sourceId));

        assertBelongsToOrg(source, callerOrgId);

        StatementParser parser = parserRegistry.resolve(source.getParserBean());
        List<NormalizedStatement> statements = parser.parse(content);

        List<NormalizedTransaction> lines = statements.stream()
                .flatMap(statement -> statement.transactions().stream())
                .toList();

        if (lines.isEmpty()) {
            log.info("Source {}: document held {} statement(s) but no lines", sourceId, statements.size());
            return new IngestionResult(statements.size(), 0, 0, 0);
        }

        Set<String> alreadyStored = findAlreadyStored(sourceId, lines);

        List<Transaction> toInsert = new ArrayList<>(lines.size());
        for (NormalizedTransaction line : lines) {
            if (alreadyStored.contains(line.externalId())) {
                continue;
            }
            toInsert.add(new Transaction(
                    sourceId,
                    line.externalId(),
                    line.amount(),
                    line.currency(),
                    line.direction(),
                    line.txnDate(),
                    line.valueDate(),
                    line.description(),
                    line.reference(),
                    line.metadata()));
        }

        transactionRepository.saveAll(toInsert);
        source.markIngested(Instant.now());

        int duplicates = lines.size() - toInsert.size();
        log.info(
                "Source {}: parsed {} line(s) from {} statement(s) — inserted {}, skipped {} duplicate(s)",
                sourceId,
                lines.size(),
                statements.size(),
                toInsert.size(),
                duplicates);

        return new IngestionResult(statements.size(), lines.size(), toInsert.size(), duplicates);
    }

    private void assertBelongsToOrg(Source source, UUID callerOrgId) {
        LegalEntity entity = legalEntityRepository
                .findById(source.getEntityId())
                .orElseThrow(() -> new SourceNotFoundException("No source " + source.getId()));

        if (callerOrgId == null || !callerOrgId.equals(entity.getOrgId())) {
            // Same error as "missing" on purpose — see SourceNotFoundException.
            throw new SourceNotFoundException("No source " + source.getId());
        }
    }

    private Set<String> findAlreadyStored(UUID sourceId, List<NormalizedTransaction> lines) {
        List<String> externalIds =
                lines.stream().map(NormalizedTransaction::externalId).distinct().toList();

        Set<String> existing = new HashSet<>();
        for (int from = 0; from < externalIds.size(); from += LOOKUP_CHUNK) {
            int to = Math.min(from + LOOKUP_CHUNK, externalIds.size());
            existing.addAll(transactionRepository.findExistingExternalIds(
                    sourceId, externalIds.subList(from, to)));
        }
        return existing;
    }
}

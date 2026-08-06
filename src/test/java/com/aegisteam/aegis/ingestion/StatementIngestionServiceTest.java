package com.aegisteam.aegis.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisteam.aegis.core.entity.LegalEntity;
import com.aegisteam.aegis.core.entity.LegalEntityRepository;
import com.aegisteam.aegis.core.source.Source;
import com.aegisteam.aegis.core.source.SourceRepository;
import com.aegisteam.aegis.core.transaction.Direction;
import com.aegisteam.aegis.core.transaction.Transaction;
import com.aegisteam.aegis.core.transaction.TransactionRepository;
import com.aegisteam.aegis.ingestion.mt940.Mt940Parser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatementIngestionServiceTest {

    private static final String STATEMENT =
            """
            :20:STMT-1
            :25:ACC/1
            :28C:1/1
            :60F:C240101INR0,00
            :61:2401020102C2500,50NTRFPAYIN//BNK-1
            :86:NEFT INWARD ACME
            :61:2401030103D750,25NCHGFEE//BNK-2
            :86:ACCOUNT FEE
            :62F:C240103INR1750,25
            """;

    private final UUID sourceId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private SourceRepository sourceRepository;
    private LegalEntityRepository legalEntityRepository;
    private TransactionRepository transactionRepository;
    private StatementIngestionService service;
    private Source source;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(SourceRepository.class);
        legalEntityRepository = mock(LegalEntityRepository.class);
        transactionRepository = mock(TransactionRepository.class);

        StatementParserRegistry registry =
                new StatementParserRegistry(Map.of("mt940Parser", new Mt940Parser()));
        service = new StatementIngestionService(
                sourceRepository, legalEntityRepository, transactionRepository, registry);

        source = mock(Source.class);
        when(source.getId()).thenReturn(sourceId);
        when(source.getEntityId()).thenReturn(entityId);
        when(source.getParserBean()).thenReturn("mt940Parser");

        LegalEntity entity = mock(LegalEntity.class);
        when(entity.getOrgId()).thenReturn(orgId);

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(legalEntityRepository.findById(entityId)).thenReturn(Optional.of(entity));
    }

    @Test
    void insertsParsedLinesAsNormalizedTransactions() {
        when(transactionRepository.findExistingExternalIds(eq(sourceId), anyList()))
                .thenReturn(List.of());

        IngestionResult result = service.ingest(sourceId, orgId, STATEMENT);

        assertEquals(1, result.statements());
        assertEquals(2, result.parsed());
        assertEquals(2, result.inserted());
        assertEquals(0, result.duplicates());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(saved.capture());

        List<Transaction> rows = saved.getValue();
        assertEquals(2, rows.size());

        Transaction credit = rows.get(0);
        assertEquals(sourceId, credit.getSourceId());
        assertEquals("BNK-1", credit.getExternalId());
        assertEquals(new BigDecimal("2500.50"), credit.getAmount());
        assertEquals("INR", credit.getCurrency());
        assertEquals(Direction.CREDIT, credit.getDirection());
        assertEquals("NEFT INWARD ACME", credit.getDescription());

        assertEquals(Direction.DEBIT, rows.get(1).getDirection());
    }

    /** Re-uploading a statement the bank re-sent must not double the ledger. */
    @Test
    void skipsLinesAlreadyStoredForThatSource() {
        when(transactionRepository.findExistingExternalIds(eq(sourceId), anyList()))
                .thenReturn(List.of("BNK-1"));

        IngestionResult result = service.ingest(sourceId, orgId, STATEMENT);

        assertEquals(2, result.parsed());
        assertEquals(1, result.inserted());
        assertEquals(1, result.duplicates());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(saved.capture());
        assertEquals(1, saved.getValue().size());
        assertEquals("BNK-2", saved.getValue().get(0).getExternalId());
    }

    @Test
    void stampsLastIngestOnTheSource() {
        when(transactionRepository.findExistingExternalIds(eq(sourceId), anyList()))
                .thenReturn(List.of());

        service.ingest(sourceId, orgId, STATEMENT);

        verify(source).markIngested(any(Instant.class));
    }

    @Test
    void rejectsUnknownSource() {
        UUID missing = UUID.randomUUID();
        when(sourceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(
                SourceNotFoundException.class, () -> service.ingest(missing, orgId, STATEMENT));
        verify(transactionRepository, never()).saveAll(anyList());
    }

    /** A source in another tenant must look identical to one that does not exist. */
    @Test
    void rejectsSourceBelongingToAnotherOrg() {
        UUID otherOrg = UUID.randomUUID();

        assertThrows(
                SourceNotFoundException.class, () -> service.ingest(sourceId, otherOrg, STATEMENT));
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsNullCallerOrg() {
        assertThrows(
                SourceNotFoundException.class, () -> service.ingest(sourceId, null, STATEMENT));
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void reportsMisconfiguredParserSeparatelyFromBadDocuments() {
        when(source.getParserBean()).thenReturn("camt053Parser");

        assertThrows(
                UnknownParserException.class, () -> service.ingest(sourceId, orgId, STATEMENT));
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void propagatesParseFailureWithoutWritingAnything() {
        assertThrows(
                StatementParseException.class,
                () -> service.ingest(sourceId, orgId, "not an mt940 file"));
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void registryResolvesByFormatLabelAsWellAsBeanName() {
        when(source.getParserBean()).thenReturn("MT940");
        when(transactionRepository.findExistingExternalIds(eq(sourceId), anyList()))
                .thenReturn(List.of());

        assertEquals(2, service.ingest(sourceId, orgId, STATEMENT).inserted());
    }
}

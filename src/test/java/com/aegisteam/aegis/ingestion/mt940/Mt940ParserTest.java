package com.aegisteam.aegis.ingestion.mt940;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisteam.aegis.core.transaction.Direction;
import com.aegisteam.aegis.ingestion.NormalizedStatement;
import com.aegisteam.aegis.ingestion.NormalizedTransaction;
import com.aegisteam.aegis.ingestion.StatementParseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class Mt940ParserTest {

    private final Mt940Parser parser = new Mt940Parser();

    private static final String SIMPLE_STATEMENT =
            """
            :20:STMT-2024-001
            :25:INBANK/1234567890
            :28C:00001/001
            :60F:C240101INR1000,00
            :61:2401020102C2500,50NTRFPAYIN-9911//BNK-REF-001
            :86:NEFT INWARD FROM ACME LTD INV-4471
            :61:2401030103D750,25NCHGFEE-2201//BNK-REF-002
            :86:MONTHLY ACCOUNT MAINTENANCE FEE
            :62F:C240103INR2750,25
            """;

    @Test
    void parsesStatementHeaderAndBalances() {
        List<NormalizedStatement> statements = parser.parse(SIMPLE_STATEMENT);

        assertEquals(1, statements.size());
        NormalizedStatement statement = statements.get(0);
        assertEquals("INBANK/1234567890", statement.accountId());
        assertEquals("00001/001", statement.statementNumber());
        assertEquals("INR", statement.currency());
        assertEquals(new BigDecimal("1000.00"), statement.openingBalance());
        assertEquals(new BigDecimal("2750.25"), statement.closingBalance());
        assertEquals(2, statement.transactions().size());
    }

    @Test
    void parsesCreditLine() {
        NormalizedTransaction credit = parser.parse(SIMPLE_STATEMENT).get(0).transactions().get(0);

        assertEquals(Direction.CREDIT, credit.direction());
        assertEquals(new BigDecimal("2500.50"), credit.amount());
        assertEquals("INR", credit.currency());
        assertEquals(LocalDate.of(2024, 1, 2), credit.valueDate());
        assertEquals(LocalDate.of(2024, 1, 2), credit.txnDate());
        assertEquals("NEFT INWARD FROM ACME LTD INV-4471", credit.description());
        assertEquals("BNK-REF-001", credit.reference());
        assertEquals("BNK-REF-001", credit.externalId());
    }

    @Test
    void parsesDebitLine() {
        NormalizedTransaction debit = parser.parse(SIMPLE_STATEMENT).get(0).transactions().get(1);

        assertEquals(Direction.DEBIT, debit.direction());
        // Amount stays positive; the sign lives in direction.
        assertEquals(new BigDecimal("750.25"), debit.amount());
        assertEquals("MONTHLY ACCOUNT MAINTENANCE FEE", debit.description());
    }

    /** Reversals invert the flow: RC moves money out, RD brings it back in. */
    @Test
    void treatsReversalMarksAsTheOppositeDirection() {
        String reversals =
                """
                :20:REV-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101EUR0,00
                :61:240102RC100,00NTRFREVCREDIT//R1
                :61:240102RD200,00NTRFREVDEBIT//R2
                :62F:C240102EUR100,00
                """;

        List<NormalizedTransaction> lines = parser.parse(reversals).get(0).transactions();

        assertEquals(Direction.DEBIT, lines.get(0).direction());
        assertEquals(Direction.CREDIT, lines.get(1).direction());
    }

    @Test
    void usesEntryDateAsBookingDateAndValueDateAsSettlement() {
        String line =
                """
                :20:D-1
                :25:ACC/1
                :28C:1/1
                :60F:C231228USD0,00
                :61:2312310102C10,00NTRFREF//B1
                :62F:C240102USD10,00
                """;

        NormalizedTransaction txn = parser.parse(line).get(0).transactions().get(0);

        // Value date 2023-12-31, entry date 01-02 -> rolls into the next year.
        assertEquals(LocalDate.of(2023, 12, 31), txn.valueDate());
        assertEquals(LocalDate.of(2024, 1, 2), txn.txnDate());
    }

    @Test
    void acceptsThousandsSeparatedAmounts() {
        String grouped =
                """
                :20:AMT-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101EUR0,00
                :61:240101C1.234.567,89NTRFREF//B1
                :62F:C240101EUR1.234.567,89
                """;

        assertEquals(
                new BigDecimal("1234567.89"),
                parser.parse(grouped).get(0).transactions().get(0).amount());
    }

    @Test
    void joinsMultiLineDescriptions() {
        String wrapped =
                """
                :20:DESC-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240101C10,00NTRFREF//B1
                :86:PAYMENT FROM ACME
                CORPORATION LIMITED
                INVOICE 12345
                :62F:C240101INR10,00
                """;

        assertEquals(
                "PAYMENT FROM ACME CORPORATION LIMITED INVOICE 12345",
                parser.parse(wrapped).get(0).transactions().get(0).description());
    }

    @Test
    void splitsMultipleStatementsInOneFile() {
        String twoStatements = SIMPLE_STATEMENT
                + """
                :20:STMT-2024-002
                :25:INBANK/1234567890
                :28C:00002/001
                :60F:C240104INR2750,25
                :61:240105C99,00NTRFREF-B//BNK-REF-003
                :62F:C240105INR2849,25
                """;

        List<NormalizedStatement> statements = parser.parse(twoStatements);

        assertEquals(2, statements.size());
        assertEquals("00002/001", statements.get(1).statementNumber());
        assertEquals(1, statements.get(1).transactions().size());
    }

    @Test
    void stripsSwiftBlockEnvelope() {
        String wrapped = "{1:F01AAAABBCCXXXX0000000000}{2:I940AAAABBCCXXXXN}{4:\n"
                + SIMPLE_STATEMENT
                + "-}";

        assertEquals(2, parser.parse(wrapped).get(0).transactions().size());
    }

    @Test
    void handlesCrLfLineEndings() {
        assertEquals(
                2, parser.parse(SIMPLE_STATEMENT.replace("\n", "\r\n")).get(0).transactions().size());
    }

    /** Some banks omit the 4-character transaction type code. */
    @Test
    void fallsBackWhenTypeCodeIsMissing() {
        String noTypeCode =
                """
                :20:NT-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240101C55,00//BNK-9
                :62F:C240101INR55,00
                """;

        NormalizedTransaction txn = parser.parse(noTypeCode).get(0).transactions().get(0);

        assertEquals(new BigDecimal("55.00"), txn.amount());
        assertEquals("BNK-9", txn.reference());
    }

    @Test
    void derivesStableIdWhenNoReferenceIsSupplied() {
        String nonref =
                """
                :20:NR-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240101C10,00NTRFNONREF
                :86:CASH DEPOSIT
                :62F:C240101INR10,00
                """;

        String first = parser.parse(nonref).get(0).transactions().get(0).externalId();
        String second = parser.parse(nonref).get(0).transactions().get(0).externalId();

        assertTrue(first.startsWith("sha256:"), "expected a derived id, got " + first);
        // Idempotency rests on this: same document, same id, every time.
        assertEquals(first, second);
    }

    /** Identical lines repeat legitimately; they must not collapse into one id. */
    @Test
    void disambiguatesRepeatedIdenticalLines() {
        String repeated =
                """
                :20:RPT-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240101C10,00NTRFNONREF
                :86:CASH DEPOSIT
                :61:240101C10,00NTRFNONREF
                :86:CASH DEPOSIT
                :62F:C240101INR20,00
                """;

        List<NormalizedTransaction> lines = parser.parse(repeated).get(0).transactions();

        assertEquals(2, lines.size());
        assertTrue(lines.get(1).externalId().endsWith("#2"));
        assertTrue(
                !lines.get(0).externalId().equals(lines.get(1).externalId()),
                "identical lines must still get distinct ids");
    }

    @Test
    void keepsRawFieldsInMetadata() {
        NormalizedTransaction txn = parser.parse(SIMPLE_STATEMENT).get(0).transactions().get(0);

        assertNotNull(txn.metadata());
        assertTrue(txn.metadata().contains("\"raw61\""));
        assertTrue(txn.metadata().contains("NTRF"));
        assertTrue(txn.metadata().contains("INBANK/1234567890"));
    }

    @Test
    void reportsFormat() {
        assertEquals("MT940", parser.format());
    }

    @Test
    void rejectsEmptyDocument() {
        assertThrows(StatementParseException.class, () -> parser.parse("   "));
    }

    @Test
    void rejectsNonMt940Content() {
        assertThrows(
                StatementParseException.class,
                () -> parser.parse("date,amount,description\n2024-01-01,10.00,hello"));
    }

    @Test
    void rejectsStatementWithoutOpeningBalance() {
        String noBalance =
                """
                :20:NB-1
                :25:ACC/1
                :28C:1/1
                :61:240101C10,00NTRFREF//B1
                :62F:C240101INR10,00
                """;

        assertThrows(StatementParseException.class, () -> parser.parse(noBalance));
    }

    @Test
    void rejectsMalformedStatementLine() {
        String broken =
                """
                :20:BAD-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:not-a-statement-line
                :62F:C240101INR0,00
                """;

        assertThrows(StatementParseException.class, () -> parser.parse(broken));
    }

    @Test
    void leavesValueDateOnlyLinesWithoutAnEntryDate() {
        String valueDateOnly =
                """
                :20:VD-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240115C10,00NTRFREF//B1
                :62F:C240115INR10,00
                """;

        NormalizedTransaction txn = parser.parse(valueDateOnly).get(0).transactions().get(0);

        assertEquals(LocalDate.of(2024, 1, 15), txn.valueDate());
        assertEquals(LocalDate.of(2024, 1, 15), txn.txnDate());
    }

    @Test
    void leavesDescriptionNullWhenNoInformationTagFollows() {
        String noDescription =
                """
                :20:ND-1
                :25:ACC/1
                :28C:1/1
                :60F:C240101INR0,00
                :61:240101C10,00NTRFREF//B1
                :62F:C240101INR10,00
                """;

        assertNull(parser.parse(noDescription).get(0).transactions().get(0).description());
    }
}

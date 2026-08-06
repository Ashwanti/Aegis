package com.aegisteam.aegis.ingestion.mt940;

import com.aegisteam.aegis.core.transaction.Direction;
import com.aegisteam.aegis.ingestion.NormalizedStatement;
import com.aegisteam.aegis.ingestion.NormalizedTransaction;
import com.aegisteam.aegis.ingestion.StatementParseException;
import com.aegisteam.aegis.ingestion.StatementParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses SWIFT MT940 customer statement messages into the normalized model.
 *
 * <p>Handles the tags that carry transaction data — :20: :25: :28C: :60F/M:
 * :61: :86: :62F/M: — and ignores the rest (:13D:, :21:, :64:, :65:) rather
 * than rejecting documents that contain them. A single file may hold several
 * statements; each :20: starts a new one.
 *
 * <p>Real-world MT940 varies more than the spec suggests, so parsing is
 * deliberately tolerant in three places: the SWIFT block envelope is stripped
 * if present, amounts accept both {@code 1.234,56} and {@code 1234,56}, and
 * a :61: line that fails the strict pattern is retried without the transaction
 * type code, which some banks omit.
 */
@Component("mt940Parser")
public class Mt940Parser implements StatementParser {

    public static final String FORMAT = "MT940";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A tag line: {@code :61:} or {@code :28C:}. Anything else continues the previous tag. */
    private static final Pattern TAG_LINE = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");

    /** Balance fields :60F: :60M: :62F: :62M: — {@code C240101INR1000,00}. */
    private static final Pattern BALANCE = Pattern.compile(
            "^(?<dcMark>[CD])(?<date>\\d{6})(?<currency>[A-Z]{3})(?<amount>[\\d.,]+)\\s*$");

    /**
     * :61: statement line. Group order follows the SWIFT field definition:
     * value date, optional entry date, D/C mark, optional funds code, amount,
     * transaction type code, owner reference, optional {@code //} bank reference.
     */
    private static final Pattern STATEMENT_LINE_STRICT = Pattern.compile(
            "^(?<valueDate>\\d{6})"
                    + "(?<entryDate>\\d{4})?"
                    + "(?<dcMark>RC|RD|C|D)"
                    + "(?<fundsCode>[A-Z])?"
                    + "(?<amount>\\d[\\d.,]{0,14})"
                    + "(?<typeCode>[A-Z][A-Z0-9]{3})"
                    + "(?<refs>.*)$");

    /** Same, for banks that omit the 4-character type code. */
    private static final Pattern STATEMENT_LINE_LENIENT = Pattern.compile(
            "^(?<valueDate>\\d{6})"
                    + "(?<entryDate>\\d{4})?"
                    + "(?<dcMark>RC|RD|C|D)"
                    + "(?<fundsCode>[A-Z])?"
                    + "(?<amount>\\d[\\d.,]{0,14})"
                    + "(?<refs>.*)$");

    private static final String NO_REFERENCE = "NONREF";
    private static final int MAX_EXTERNAL_ID = 255;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_REFERENCE = 255;

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public List<NormalizedStatement> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new StatementParseException("Document is empty");
        }

        List<Tag> tags = readTags(stripSwiftEnvelope(content));
        if (tags.isEmpty()) {
            throw new StatementParseException(
                    "No MT940 tags found — the document does not look like an MT940 statement");
        }

        List<NormalizedStatement> statements = new ArrayList<>();
        StatementBuilder current = null;
        // Spans the whole document: uniqueness is per (source, external_id), so
        // two statements in one file must not hand back the same id.
        Map<String, Integer> seenExternalIds = new HashMap<>();

        for (Tag tag : tags) {
            switch (tag.name()) {
                case "20" -> {
                    if (current != null) {
                        statements.add(current.build(seenExternalIds));
                    }
                    current = new StatementBuilder();
                    current.reference = tag.value().trim();
                }
                case "25" -> requireStatement(current, "25").accountId = tag.value().trim();
                case "28", "28C" -> requireStatement(current, "28C").statementNumber = tag.value().trim();
                case "60F", "60M" -> {
                    StatementBuilder s = requireStatement(current, "60");
                    Balance opening = parseBalance(tag.value());
                    s.currency = opening.currency();
                    s.openingBalance = opening.signedAmount();
                }
                case "62F", "62M" -> requireStatement(current, "62").closingBalance =
                        parseBalance(tag.value()).signedAmount();
                case "61" -> requireStatement(current, "61").addLine(tag.value());
                case "86" -> {
                    StatementBuilder s = requireStatement(current, "86");
                    s.attachDescription(tag.value());
                }
                default -> {
                    // :13D:, :21:, :64:, :65: and friends carry no transaction data.
                }
            }
        }

        if (current != null) {
            statements.add(current.build(seenExternalIds));
        }

        if (statements.isEmpty()) {
            throw new StatementParseException("No statements found — missing :20: reference tag");
        }
        return List.copyOf(statements);
    }

    /**
     * Real MT940 downloads are often wrapped in SWIFT blocks
     * ({@code {1:...}{2:...}{4: ... -}}). Take the block 4 payloads when present.
     */
    private static String stripSwiftEnvelope(String content) {
        if (!content.contains("{4:")) {
            return content;
        }
        StringBuilder payload = new StringBuilder();
        int from = 0;
        while (true) {
            int start = content.indexOf("{4:", from);
            if (start < 0) {
                break;
            }
            int bodyStart = start + "{4:".length();
            int end = content.indexOf("-}", bodyStart);
            if (end < 0) {
                payload.append(content.substring(bodyStart));
                break;
            }
            payload.append(content, bodyStart, end).append('\n');
            from = end + "-}".length();
        }
        return payload.length() == 0 ? content : payload.toString();
    }

    /** Folds continuation lines into the tag they belong to. */
    private static List<Tag> readTags(String content) {
        List<Tag> tags = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        String name = null;

        for (String rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.stripTrailing();
            Matcher matcher = TAG_LINE.matcher(line);
            if (matcher.matches()) {
                if (name != null) {
                    tags.add(new Tag(name, value.toString()));
                }
                name = matcher.group(1);
                value = new StringBuilder(matcher.group(2));
            } else if (name != null) {
                if (line.equals("-") || line.equals("-}")) {
                    continue; // end-of-message marker
                }
                value.append('\n').append(line);
            }
        }
        if (name != null) {
            tags.add(new Tag(name, value.toString()));
        }
        return tags;
    }

    private static StatementBuilder requireStatement(StatementBuilder current, String tag) {
        if (current == null) {
            throw new StatementParseException(
                    "Tag :" + tag + ": appeared before the :20: that starts a statement");
        }
        return current;
    }

    private static Balance parseBalance(String value) {
        Matcher matcher = BALANCE.matcher(value.trim());
        if (!matcher.matches()) {
            throw new StatementParseException("Malformed balance field: " + value.trim());
        }
        BigDecimal amount = parseAmount(matcher.group("amount"));
        boolean debit = "D".equals(matcher.group("dcMark"));
        return new Balance(matcher.group("currency"), debit ? amount.negate() : amount);
    }

    /** MT940 uses comma as the decimal separator; dots, when present, group thousands. */
    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw.trim().replace(".", "").replace(',', '.');
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            throw new StatementParseException("Malformed amount: " + raw);
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new StatementParseException("Malformed amount: " + raw, e);
        }
    }

    private static LocalDate parseDate(String yymmdd) {
        try {
            int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
            int month = Integer.parseInt(yymmdd.substring(2, 4));
            int day = Integer.parseInt(yymmdd.substring(4, 6));
            return LocalDate.of(year, month, day);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            throw new StatementParseException("Malformed date: " + yymmdd, e);
        }
    }

    /**
     * Entry date is MMDD with no year. Anchor it to the value date's year,
     * stepping a year when the two straddle a year boundary.
     */
    private static LocalDate resolveEntryDate(String mmdd, LocalDate valueDate) {
        int month = Integer.parseInt(mmdd.substring(0, 2));
        int day = Integer.parseInt(mmdd.substring(2, 4));
        int year = valueDate.getYear();
        if (month - valueDate.getMonthValue() > 6) {
            year -= 1;
        } else if (valueDate.getMonthValue() - month > 6) {
            year += 1;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            throw new StatementParseException("Malformed entry date: " + mmdd, e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private record Tag(String name, String value) {}

    private record Balance(String currency, BigDecimal signedAmount) {}

    /** A :61: line plus the :86: that may follow it. */
    private static final class Line {
        private final String raw61;
        private String raw86;

        private Line(String raw61) {
            this.raw61 = raw61;
        }
    }

    private static final class StatementBuilder {
        private String reference;
        private String accountId;
        private String statementNumber;
        private String currency;
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private final List<Line> lines = new ArrayList<>();

        private void addLine(String raw61) {
            lines.add(new Line(raw61));
        }

        /**
         * A :86: directly after a :61: describes that line. One that arrives
         * before any :61: — or after the closing balance — is statement-level
         * narrative and is dropped.
         */
        private void attachDescription(String value) {
            if (lines.isEmpty()) {
                return;
            }
            Line last = lines.get(lines.size() - 1);
            last.raw86 = last.raw86 == null ? value : last.raw86 + "\n" + value;
        }

        private NormalizedStatement build(Map<String, Integer> seen) {
            if (currency == null) {
                throw new StatementParseException(
                        "Statement " + (reference == null ? "" : reference)
                                + " has no :60F: opening balance, so its currency is unknown");
            }

            List<NormalizedTransaction> transactions = new ArrayList<>(lines.size());

            for (Line line : lines) {
                transactions.add(toTransaction(line, seen));
            }

            return new NormalizedStatement(
                    accountId, statementNumber, currency, openingBalance, closingBalance, transactions);
        }

        private NormalizedTransaction toTransaction(Line line, Map<String, Integer> seen) {
            String first = line.raw61.split("\n", 2)[0].trim();

            Matcher matcher = STATEMENT_LINE_STRICT.matcher(first);
            boolean strict = matcher.matches();
            if (!strict) {
                matcher = STATEMENT_LINE_LENIENT.matcher(first);
                if (!matcher.matches()) {
                    throw new StatementParseException("Malformed :61: statement line: " + first);
                }
            }

            LocalDate valueDate = parseDate(matcher.group("valueDate"));
            String entryDateGroup = matcher.group("entryDate");
            LocalDate txnDate =
                    entryDateGroup == null ? valueDate : resolveEntryDate(entryDateGroup, valueDate);

            String dcMark = matcher.group("dcMark");
            // RC/RD are reversals: a reversed credit moves money out, and vice versa.
            Direction direction = switch (dcMark) {
                case "C", "RD" -> Direction.CREDIT;
                case "D", "RC" -> Direction.DEBIT;
                default -> throw new StatementParseException("Unknown debit/credit mark: " + dcMark);
            };

            BigDecimal amount = parseAmount(matcher.group("amount"));
            String typeCode = strict ? matcher.group("typeCode") : null;

            String refs = matcher.group("refs") == null ? "" : matcher.group("refs").trim();
            String ownerRef;
            String bankRef = null;
            int split = refs.indexOf("//");
            if (split >= 0) {
                ownerRef = refs.substring(0, split).trim();
                bankRef = refs.substring(split + 2).trim();
            } else {
                ownerRef = refs;
            }

            String description = buildDescription(line.raw86);
            String externalId = externalId(bankRef, ownerRef, line, seen);
            String reference = usable(bankRef) ? bankRef : (usable(ownerRef) ? ownerRef : null);

            return new NormalizedTransaction(
                    externalId,
                    amount,
                    currency,
                    direction,
                    txnDate,
                    valueDate,
                    truncate(description, MAX_DESCRIPTION),
                    truncate(reference, MAX_REFERENCE),
                    metadata(line, typeCode, matcher.group("fundsCode"), ownerRef, bankRef));
        }

        private static String buildDescription(String raw86) {
            if (raw86 == null) {
                return null;
            }
            // :86: continuation lines are a wrapped sentence, not separate fields.
            return raw86.replace("\n", " ").replaceAll("\\s+", " ").trim();
        }

        /**
         * The bank's own reference is the best identity when it gives one.
         * Otherwise fall back to a hash of the line itself so that re-uploading
         * the same document is idempotent. Identical lines legitimately repeat
         * within a statement, so collisions get a stable ordinal suffix rather
         * than being merged.
         */
        private String externalId(String bankRef, String ownerRef, Line line, Map<String, Integer> seen) {
            String base;
            if (usable(bankRef)) {
                base = bankRef;
            } else if (usable(ownerRef)) {
                base = ownerRef;
            } else {
                base = sha256(accountId + "|" + statementNumber + "|" + line.raw61 + "|" + line.raw86);
            }

            base = truncate(base, MAX_EXTERNAL_ID - 8);
            int occurrence = seen.merge(base, 1, Integer::sum);
            return occurrence == 1 ? base : base + "#" + occurrence;
        }

        private static boolean usable(String ref) {
            return ref != null && !ref.isBlank() && !NO_REFERENCE.equalsIgnoreCase(ref.trim());
        }

        private String metadata(
                Line line, String typeCode, String fundsCode, String ownerRef, String bankRef) {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("format", FORMAT);
            raw.put("accountId", accountId);
            raw.put("statementNumber", statementNumber);
            raw.put("statementReference", reference);
            raw.put("transactionTypeCode", typeCode);
            raw.put("fundsCode", fundsCode);
            raw.put("ownerReference", emptyToNull(ownerRef));
            raw.put("bankReference", emptyToNull(bankRef));
            raw.put("raw61", line.raw61);
            raw.put("raw86", line.raw86);
            raw.values().removeIf(java.util.Objects::isNull);
            return JSON.writeValueAsString(raw);
        }

        private static String emptyToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}

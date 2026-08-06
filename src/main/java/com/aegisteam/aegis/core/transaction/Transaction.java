package com.aegisteam.aegis.core.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Maps the {@code transactions} table (V005) — the normalized form every parsed
 * document lands in, regardless of the format it arrived as.
 *
 * <p>{@code created_at}, {@code ingested_at} and {@code status} carry database
 * defaults, but they are set here in Java as well: Hibernate includes every
 * mapped column in its INSERT, so an unset field would send an explicit NULL
 * and trip the NOT NULL constraint rather than fall back to the default.
 */
@Entity
@Table(name = "transactions")
@SQLDelete(sql = "UPDATE transactions SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    /** Stable per (source, document) so re-uploading a file is idempotent. */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "normalized_amount", precision = 18, scale = 6)
    private BigDecimal normalizedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    /** Raw source fields, kept so a parse can be audited after the fact. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {
        // JPA
    }

    public Transaction(
            UUID sourceId,
            String externalId,
            BigDecimal amount,
            String currency,
            Direction direction,
            LocalDate txnDate,
            LocalDate valueDate,
            String description,
            String reference,
            String metadata) {

        Instant now = Instant.now();

        this.sourceId = sourceId;
        this.externalId = externalId;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.txnDate = txnDate;
        this.valueDate = valueDate;
        this.description = description;
        this.reference = reference;
        this.metadata = metadata;
        this.status = TransactionStatus.PENDING;
        this.ingestedAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getExternalId() {
        return externalId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getNormalizedAmount() {
        return normalizedAmount;
    }

    public Direction getDirection() {
        return direction;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

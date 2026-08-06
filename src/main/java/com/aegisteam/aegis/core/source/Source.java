package com.aegisteam.aegis.core.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Maps the {@code sources} table (V002). {@code parser_bean} names the Spring
 * bean that knows how to read this source's documents — that indirection is
 * what lets one org send MT940 while another sends something else later,
 * without the ingestion service knowing the difference.
 *
 * <p>{@code credentials_enc} and {@code cron} are intentionally unmapped: they
 * belong to the scheduled-pull path, and leaving them off keeps this entity
 * from touching columns it has no business writing.
 */
@Entity
@Table(name = "sources")
@SQLDelete(sql = "UPDATE sources SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Source {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType type;

    @Column(name = "parser_bean", nullable = false)
    private String parserBean;

    @Column(name = "last_ingest_at")
    private Instant lastIngestAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Source() {
        // JPA
    }

    public Source(UUID entityId, String name, SourceType type, String parserBean) {
        this.entityId = entityId;
        this.name = name;
        this.type = type;
        this.parserBean = parserBean;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getName() {
        return name;
    }

    public SourceType getType() {
        return type;
    }

    public String getParserBean() {
        return parserBean;
    }

    public Instant getLastIngestAt() {
        return lastIngestAt;
    }

    public void markIngested(Instant at) {
        this.lastIngestAt = at;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

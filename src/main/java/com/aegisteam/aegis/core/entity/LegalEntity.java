package com.aegisteam.aegis.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Maps the {@code entities} table (V001). Named LegalEntity to avoid colliding
 * with JPA's own "entity" vocabulary.
 *
 * <p>Deliberately does not extend {@code BaseEntity}: that adds a non-null
 * {@code updated_at} which this table does not have, and with
 * {@code ddl-auto=update} an inherited mapping would silently alter the schema.
 * Sits between org and source, so it is what a tenancy check resolves through.
 */
@Entity
@Table(name = "entities")
@SQLDelete(sql = "UPDATE entities SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LegalEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LegalEntity() {
        // JPA
    }

    public LegalEntity(UUID orgId, String name) {
        this.orgId = orgId;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getName() {
        return name;
    }

    public String getTaxId() {
        return taxId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.aegisteam.aegis.core.org;

import com.aegisteam.aegis.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Tenant root. Every {@link com.aegisteam.aegis.core.user.User} belongs to
 * exactly one org via {@code users.org_id}. {@code base_currency} and
 * {@code plan} are left to their column defaults ('INR'/'BASIC') until there
 * is a flow that sets them.
 */
@Entity
@Table(name = "orgs")
@SQLDelete(sql = "UPDATE orgs SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Org extends BaseEntity {

    @Column(nullable = false)
    private String name;

    protected Org() {
        // JPA
    }

    public Org(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

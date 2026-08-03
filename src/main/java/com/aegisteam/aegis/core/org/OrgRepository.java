package com.aegisteam.aegis.core.org;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgRepository extends JpaRepository<Org, UUID> {

    /**
     * Case-insensitive so "Acme Corp" and "acme corp" resolve to one tenant
     * rather than two. Soft-deleted orgs are excluded by the entity's
     * {@code @SQLRestriction}.
     */
    Optional<Org> findByNameIgnoreCase(String name);
}

package com.aegisteam.aegis.core.transaction;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Used to skip rows a previous upload already stored. Returns only the ids,
     * so re-ingesting a large statement does not pull whole rows into memory
     * just to discard them.
     */
    @Query("select t.externalId from Transaction t "
            + "where t.sourceId = :sourceId and t.externalId in :externalIds")
    List<String> findExistingExternalIds(UUID sourceId, Collection<String> externalIds);
}

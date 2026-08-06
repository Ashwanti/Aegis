package com.aegisteam.aegis.core.entity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {}

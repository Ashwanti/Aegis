package com.aegisteam.aegis.core.source;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {}

package com.migration.repository.jpa;

import com.migration.model.jpa.BackfillCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackfillCheckpointRepository extends JpaRepository<BackfillCheckpointEntity, String> {
}

package com.migration.repository.jpa;

import com.migration.model.jpa.BackfillDlqEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackfillDlqRepository extends JpaRepository<BackfillDlqEntity, Integer> {

    List<BackfillDlqEntity> findByResolvedFalse();
}

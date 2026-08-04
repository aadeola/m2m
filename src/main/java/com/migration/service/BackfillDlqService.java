package com.migration.service;

import com.migration.dto.BackfillDlqResponse;
import com.migration.model.jpa.BackfillDlqEntity;
import com.migration.repository.jpa.BackfillDlqRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BackfillDlqService {

    private final BackfillDlqRepository backfillDlqRepository;

    public BackfillDlqService(BackfillDlqRepository backfillDlqRepository) {
        this.backfillDlqRepository = backfillDlqRepository;
    }

    public List<BackfillDlqResponse> listFailures(Boolean resolved) {
        List<BackfillDlqEntity> entities;
        if (Boolean.TRUE.equals(resolved)) {
            entities = backfillDlqRepository.findByResolvedTrue();
        } else {
            entities = backfillDlqRepository.findByResolvedFalse();
        }
        return entities.stream().map(this::toResponse).toList();
    }

    private BackfillDlqResponse toResponse(BackfillDlqEntity entity) {
        BackfillDlqResponse response = new BackfillDlqResponse();
        response.setId(entity.getId());
        response.setEntityName(entity.getEntityName());
        response.setStartPk(entity.getStartPk());
        response.setEndPk(entity.getEndPk());
        response.setExceptionClass(entity.getExceptionClass());
        response.setMessage(entity.getMessage());
        response.setOccurredAt(entity.getOccurredAt());
        response.setResolved(entity.isResolved());
        response.setResolvedAt(entity.getResolvedAt());
        return response;
    }
}

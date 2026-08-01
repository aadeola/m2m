package com.migration.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "backfill_checkpoint")
public class BackfillCheckpointEntity {

    @Id
    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "last_processed_pk", nullable = false)
    private Integer lastProcessedPk;

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Integer getLastProcessedPk() {
        return lastProcessedPk;
    }

    public void setLastProcessedPk(Integer lastProcessedPk) {
        this.lastProcessedPk = lastProcessedPk;
    }
}

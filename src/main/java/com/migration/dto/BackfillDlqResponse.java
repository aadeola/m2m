package com.migration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class BackfillDlqResponse {

    private Integer id;

    @JsonProperty("entity_name")
    private String entityName;

    @JsonProperty("start_pk")
    private Integer startPk;

    @JsonProperty("end_pk")
    private Integer endPk;

    @JsonProperty("exception_class")
    private String exceptionClass;

    private String message;

    @JsonProperty("occurred_at")
    private LocalDateTime occurredAt;

    private boolean resolved;

    @JsonProperty("resolved_at")
    private LocalDateTime resolvedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Integer getStartPk() {
        return startPk;
    }

    public void setStartPk(Integer startPk) {
        this.startPk = startPk;
    }

    public Integer getEndPk() {
        return endPk;
    }

    public void setEndPk(Integer endPk) {
        this.endPk = endPk;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

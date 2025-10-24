package com.example.dmsyncbridge.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncLog {

    private Long id;
    private String sourceDb;
    private String targetDb;
    private String tableName;
    private String operationType;
    private String status;
    private LocalDateTime createTime;
    private String message;

    public SyncLog() {
    }

    public SyncLog(Long id, String sourceDb, String targetDb, String tableName, String operationType,
                   String status, LocalDateTime createTime, String message) {
        this.id = id;
        this.sourceDb = sourceDb;
        this.targetDb = targetDb;
        this.tableName = tableName;
        this.operationType = operationType;
        this.status = status;
        this.createTime = createTime;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceDb() {
        return sourceDb;
    }

    public void setSourceDb(String sourceDb) {
        this.sourceDb = sourceDb;
    }

    public String getTargetDb() {
        return targetDb;
    }

    public void setTargetDb(String targetDb) {
        this.targetDb = targetDb;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
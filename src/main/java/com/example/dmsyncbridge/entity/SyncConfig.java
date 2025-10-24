package com.example.dmsyncbridge.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncConfig {

    private Long id;
    private String tableName;
    private String primaryKey;
    private List<String> includeColumns = new ArrayList<>();
    private String lastUpdateColumn;
    private boolean activeFlag = true;

    @JsonIgnore
    private Instant lastSyncTime;

    public SyncConfig() {
    }

    public SyncConfig(Long id, String tableName, String primaryKey, List<String> includeColumns,
                      String lastUpdateColumn, boolean activeFlag) {
        this.id = id;
        this.tableName = tableName;
        this.primaryKey = primaryKey;
        if (includeColumns != null) {
            this.includeColumns = new ArrayList<>(includeColumns);
        }
        this.lastUpdateColumn = lastUpdateColumn;
        this.activeFlag = activeFlag;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public List<String> getIncludeColumns() {
        return Collections.unmodifiableList(includeColumns);
    }

    public void setIncludeColumns(List<String> includeColumns) {
        this.includeColumns = includeColumns == null ? new ArrayList<>() : new ArrayList<>(includeColumns);
    }

    public String getLastUpdateColumn() {
        return lastUpdateColumn;
    }

    public void setLastUpdateColumn(String lastUpdateColumn) {
        this.lastUpdateColumn = lastUpdateColumn;
    }

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    public Instant getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(Instant lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncConfig that = (SyncConfig) o;
        return Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName);
    }
}
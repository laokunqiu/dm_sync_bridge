package com.example.dmsyncbridge.service;

import com.example.dmsyncbridge.entity.SyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DmSyncService {

    private static final Logger log = LoggerFactory.getLogger(DmSyncService.class);

    private final JdbcTemplate dbAJdbcTemplate;
    private final JdbcTemplate dbBJdbcTemplate;
    private final DataSource dbADataSource;
    private final DataSource dbBDataSource;
    private final SyncConfigService configService;
    private final SyncLogService syncLogService;
    private final Map<String, Queue<SyncOperation>> pendingByTarget = new ConcurrentHashMap<>();

    public DmSyncService(@Qualifier("dbAJdbcTemplate") JdbcTemplate dbAJdbcTemplate,
                         @Qualifier("dbBJdbcTemplate") JdbcTemplate dbBJdbcTemplate,
                         @Qualifier("dbADataSource") DataSource dbADataSource,
                         @Qualifier("dbBDataSource") DataSource dbBDataSource,
                         SyncConfigService configService,
                         SyncLogService syncLogService) {
        this.dbAJdbcTemplate = dbAJdbcTemplate;
        this.dbBJdbcTemplate = dbBJdbcTemplate;
        this.dbADataSource = dbADataSource;
        this.dbBDataSource = dbBDataSource;
        this.configService = configService;
        this.syncLogService = syncLogService;
    }

    public void synchronizeAll() {
        replayPendingOperations("dbA", dbAJdbcTemplate);
        replayPendingOperations("dbB", dbBJdbcTemplate);

        List<SyncConfig> configs = configService.findAll();
        for (SyncConfig config : configs) {
            if (!config.isActiveFlag()) {
                log.debug("Skipping inactive config for table {}", config.getTableName());
                continue;
            }
            try {
                synchronizeTable(config);
                configService.updateLastSyncTime(config.getTableName(), Instant.now());
            } catch (Exception e) {
                log.error("Failed to synchronize table {}: {}", config.getTableName(), e.getMessage(), e);
                syncLogService.record("dbA", "dbB", config.getTableName(), "SYNC", "FAILED", e.getMessage());
            }
        }
    }

    public void synchronizeTables(Collection<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames)) {
            synchronizeAll();
            return;
        }
        for (String tableName : tableNames) {
            Optional<SyncConfig> configOptional = configService.findByTableName(tableName);
            configOptional.ifPresent(this::synchronizeTableSafely);
        }
    }

    private void synchronizeTableSafely(SyncConfig config) {
        try {
            synchronizeTable(config);
            configService.updateLastSyncTime(config.getTableName(), Instant.now());
        } catch (Exception e) {
            log.error("Failed to synchronize table {}: {}", config.getTableName(), e.getMessage(), e);
            syncLogService.record("dbA", "dbB", config.getTableName(), "SYNC", "FAILED", e.getMessage());
        }
    }

    private void synchronizeTable(SyncConfig config) {
        log.info("Synchronizing table {}", config.getTableName());
        Map<String, Object> fetchContext = buildFetchContext(config);
        List<Map<String, Object>> rowsA = fetchRows(dbAJdbcTemplate, config, fetchContext);
        List<Map<String, Object>> rowsB = fetchRows(dbBJdbcTemplate, config, fetchContext);

        Map<Object, Map<String, Object>> mapA = indexByPrimaryKey(rowsA, config.getPrimaryKey());
        Map<Object, Map<String, Object>> mapB = indexByPrimaryKey(rowsB, config.getPrimaryKey());

        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(mapA.keySet());
        allKeys.addAll(mapB.keySet());

        for (Object key : allKeys) {
            Map<String, Object> rowA = mapA.get(key);
            Map<String, Object> rowB = mapB.get(key);

            if (rowA == null) {
                queueOperation("dbA", "dbB", config, OperationType.DELETE, rowB, key);
            } else if (rowB == null) {
                queueOperation("dbB", "dbA", config, OperationType.INSERT, rowA, key);
            } else if (hasDifferences(rowA, rowB, config)) {
                Instant lastUpdateA = getUpdateTimestamp(rowA, config);
                Instant lastUpdateB = getUpdateTimestamp(rowB, config);
                if (lastUpdateA != null && lastUpdateB != null) {
                    if (lastUpdateA.isAfter(lastUpdateB)) {
                        queueOperation("dbB", "dbA", config, OperationType.UPDATE, rowA, key);
                    } else if (lastUpdateB.isAfter(lastUpdateA)) {
                        queueOperation("dbA", "dbB", config, OperationType.UPDATE, rowB, key);
                    } else {
                        queueOperation("dbB", "dbA", config, OperationType.UPDATE, rowA, key);
                        queueOperation("dbA", "dbB", config, OperationType.UPDATE, rowB, key);
                    }
                } else {
                    queueOperation("dbB", "dbA", config, OperationType.UPDATE, rowA, key);
                    queueOperation("dbA", "dbB", config, OperationType.UPDATE, rowB, key);
                }
            }
        }

        flushPending();
        syncLogService.record("dbA", "dbB", config.getTableName(), "SYNC", "SUCCESS",
                "Synchronization completed with " + allKeys.size() + " keys inspected");
    }

    private Map<String, Object> buildFetchContext(SyncConfig config) {
        Map<String, Object> context = new HashMap<>();
        context.put("lastSyncTime", config.getLastSyncTime());
        return context;
    }

    private List<Map<String, Object>> fetchRows(JdbcTemplate jdbcTemplate, SyncConfig config, Map<String, Object> context) {
        String selectColumns = buildSelectColumns(config);
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns)
                .append(" FROM ").append(config.getTableName());
        List<Object> params = new ArrayList<>();
        if (config.getLastUpdateColumn() != null && context.get("lastSyncTime") instanceof Instant) {
            sql.append(" WHERE ").append(config.getLastUpdateColumn()).append(" >= ?");
            Instant lastSyncTime = ((Instant) context.get("lastSyncTime")).minus(5, ChronoUnit.SECONDS);
            params.add(Timestamp.from(lastSyncTime));
        }
        try {
            return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnLabel(i);
                    Object value = rs.getObject(i);
                    map.put(columnName.toLowerCase(), value);
                }
                return map;
            });
        } catch (Exception e) {
            log.warn("Failed to fetch rows for table {}: {}", config.getTableName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildSelectColumns(SyncConfig config) {
        Set<String> columns = new HashSet<>(config.getIncludeColumns());
        columns.add(config.getPrimaryKey());
        if (config.getLastUpdateColumn() != null) {
            columns.add(config.getLastUpdateColumn());
        }
        columns.remove("*");
        if (columns.isEmpty()) {
            return "*";
        }
        List<String> sorted = new ArrayList<>(columns);
        sorted.sort(Comparator.naturalOrder());
        return String.join(", ", sorted);
    }

    private Map<Object, Map<String, Object>> indexByPrimaryKey(List<Map<String, Object>> rows, String primaryKey) {
        Map<Object, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(primaryKey.toLowerCase());
            result.put(key, row);
        }
        return result;
    }

    private boolean hasDifferences(Map<String, Object> rowA, Map<String, Object> rowB, SyncConfig config) {
        Set<String> columnsToCompare = new HashSet<>(config.getIncludeColumns());
        if (columnsToCompare.isEmpty() || columnsToCompare.contains("*")) {
            columnsToCompare.addAll(rowA.keySet());
            columnsToCompare.addAll(rowB.keySet());
        }
        columnsToCompare.remove(config.getPrimaryKey().toLowerCase());
        columnsToCompare.remove(config.getLastUpdateColumn() != null ? config.getLastUpdateColumn().toLowerCase() : null);
        for (String column : columnsToCompare) {
            if (column == null) {
                continue;
            }
            Object valueA = rowA.get(column.toLowerCase());
            Object valueB = rowB.get(column.toLowerCase());
            if (!Objects.equals(valueA, valueB)) {
                return true;
            }
        }
        return false;
    }

    private Instant getUpdateTimestamp(Map<String, Object> row, SyncConfig config) {
        if (config.getLastUpdateColumn() == null) {
            return null;
        }
        Object value = row.get(config.getLastUpdateColumn().toLowerCase());
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant();
        }
        return null;
    }

    private void queueOperation(String sourceDb, String targetDb, SyncConfig config, OperationType operationType,
                                Map<String, Object> rowData, Object key) {
        if (rowData == null) {
            return;
        }
        SyncOperation operation = new SyncOperation(sourceDb, targetDb, config, operationType, rowData, key);
        pendingByTarget.computeIfAbsent(targetDb, k -> new ConcurrentLinkedQueue<>()).add(operation);
    }

    private void flushPending() {
        replayPendingOperations("dbA", dbAJdbcTemplate);
        replayPendingOperations("dbB", dbBJdbcTemplate);
    }

    private void replayPendingOperations(String targetDb, JdbcTemplate jdbcTemplate) {
        Queue<SyncOperation> queue = pendingByTarget.getOrDefault(targetDb, new ConcurrentLinkedQueue<>());
        int size = queue.size();
        Instant now = Instant.now();
        for (int i = 0; i < size; i++) {
            SyncOperation operation = queue.poll();
            if (operation == null) {
                continue;
            }
            if (operation.getNextRetryTime() != null && now.isBefore(operation.getNextRetryTime())) {
                queue.offer(operation);
                continue;
            }
            boolean success = applyOperation(jdbcTemplate, operation);
            if (!success) {
                queue.offer(operation);
            }
        }
    }

    private boolean applyOperation(JdbcTemplate jdbcTemplate, SyncOperation operation) {
        int attempt = operation.incrementAttempts();
        try {
            switch (operation.operationType) {
                case INSERT:
                    executeInsert(jdbcTemplate, operation);
                    break;
                case UPDATE:
                    executeUpdate(jdbcTemplate, operation);
                    break;
                case DELETE:
                    executeDelete(jdbcTemplate, operation);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation " + operation.operationType);
            }
            syncLogService.record(operation.sourceDb, operation.targetDb, operation.config.getTableName(),
                    operation.operationType.name(), "SUCCESS",
                    "Row synchronized for key " + operation.primaryKeyValue);
            return true;
        } catch (Exception ex) {
            if (attempt < 3) {
                long backoffMillis = (long) Math.pow(2, attempt) * 500L;
                operation.setNextRetryTime(Instant.now().plusMillis(backoffMillis));
                log.warn("Retrying operation {} on table {} after {} ms due to: {}", operation.operationType,
                        operation.config.getTableName(), backoffMillis, ex.getMessage());
            } else {
                syncLogService.record(operation.sourceDb, operation.targetDb, operation.config.getTableName(),
                        operation.operationType.name(), "FAILED", ex.getMessage());
                log.error("Operation {} failed permanently on table {}: {}", operation.operationType,
                        operation.config.getTableName(), ex.getMessage(), ex);
                return true; // discard after max attempts
            }
            return false;
        }
    }

    private void executeInsert(JdbcTemplate jdbcTemplate, SyncOperation operation) {
        Map<String, Object> rowData = operation.rowData;
        List<String> columns = new ArrayList<>(rowData.keySet());
        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String insertSql = "INSERT INTO " + operation.config.getTableName() + " (" + columnList + ") VALUES (" + placeholders + ")";
        List<Object> values = new ArrayList<>();
        for (String column : columns) {
            values.add(rowData.get(column));
        }
        try {
            jdbcTemplate.update(insertSql, values.toArray());
        } catch (org.springframework.dao.DataAccessException ex) {
            log.debug("Insert failed for table {} key {}. Attempting update instead: {}", operation.config.getTableName(),
                    operation.primaryKeyValue, ex.getMessage());
            executeUpdate(jdbcTemplate, operation, false);
        }
    }

    private void executeUpdate(JdbcTemplate jdbcTemplate, SyncOperation operation) {
        executeUpdate(jdbcTemplate, operation, true);
    }

    private void executeUpdate(JdbcTemplate jdbcTemplate, SyncOperation operation, boolean allowInsertFallback) {
        Map<String, Object> rowData = operation.rowData;
        List<String> columns = new ArrayList<>(rowData.keySet());
        columns.remove(operation.config.getPrimaryKey().toLowerCase());
        String assignments = buildUpdateAssignments(columns, null);
        String sql = "UPDATE " + operation.config.getTableName() + " SET " + assignments +
                " WHERE " + operation.config.getPrimaryKey() + " = ?";
        List<Object> values = new ArrayList<>();
        for (String column : columns) {
            values.add(rowData.get(column));
        }
        values.add(operation.primaryKeyValue);
        int updated = jdbcTemplate.update(sql, values.toArray());
        if (allowInsertFallback && updated == 0) {
            executeInsert(jdbcTemplate, operation);
        }
    }

    private void executeDelete(JdbcTemplate jdbcTemplate, SyncOperation operation) {
        String sql = "DELETE FROM " + operation.config.getTableName() +
                " WHERE " + operation.config.getPrimaryKey() + " = ?";
        jdbcTemplate.update(sql, operation.primaryKeyValue);
    }

    private String buildUpdateAssignments(List<String> columns, String skipColumn) {
        List<String> assignments = new ArrayList<>();
        for (String column : columns) {
            if (column == null) {
                continue;
            }
            if (skipColumn != null && skipColumn.equalsIgnoreCase(column)) {
                continue;
            }
            assignments.add(column + " = ?");
        }
        return String.join(", ", assignments);
    }

    public boolean isDatabaseAvailable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isDbAAvailable() {
        return isDatabaseAvailable(dbADataSource);
    }

    public boolean isDbBAvailable() {
        return isDatabaseAvailable(dbBDataSource);
    }

    public int getPendingOperationCount(String targetDb) {
        Queue<SyncOperation> queue = pendingByTarget.get(targetDb);
        return queue == null ? 0 : queue.size();
    }

    private enum OperationType {
        INSERT, UPDATE, DELETE
    }

    private static class SyncOperation {
        private final String sourceDb;
        private final String targetDb;
        private final SyncConfig config;
        private final OperationType operationType;
        private final Map<String, Object> rowData;
        private final Object primaryKeyValue;
        private int attempts;
        private Instant nextRetryTime;

        private SyncOperation(String sourceDb, String targetDb, SyncConfig config, OperationType operationType,
                              Map<String, Object> rowData, Object primaryKeyValue) {
            this.sourceDb = sourceDb;
            this.targetDb = targetDb;
            this.config = config;
            this.operationType = operationType;
            this.rowData = new HashMap<>();
            rowData.forEach((k, v) -> this.rowData.put(k.toLowerCase(), v));
            this.primaryKeyValue = primaryKeyValue;
        }

        private int incrementAttempts() {
            return ++attempts;
        }

        public void setNextRetryTime(Instant nextRetryTime) {
            this.nextRetryTime = nextRetryTime;
        }

        public Instant getNextRetryTime() {
            return nextRetryTime;
        }
    }
}
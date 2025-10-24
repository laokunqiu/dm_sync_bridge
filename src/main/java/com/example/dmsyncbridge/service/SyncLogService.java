package com.example.dmsyncbridge.service;

import com.example.dmsyncbridge.entity.SyncLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class SyncLogService {

    private static final Logger log = LoggerFactory.getLogger(SyncLogService.class);

    private final JdbcTemplate dbAJdbcTemplate;
    private final List<SyncLog> inMemoryLogs = Collections.synchronizedList(new LinkedList<>());

    public SyncLogService(@Qualifier("dbAJdbcTemplate") JdbcTemplate dbAJdbcTemplate) {
        this.dbAJdbcTemplate = dbAJdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        try {
            dbAJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sync_log (" +
                    "id IDENTITY PRIMARY KEY, " +
                    "source_db VARCHAR(64), " +
                    "target_db VARCHAR(64), " +
                    "table_name VARCHAR(128), " +
                    "operation_type VARCHAR(32), " +
                    "status VARCHAR(32), " +
                    "create_time TIMESTAMP, " +
                    "message VARCHAR(4000))");
        } catch (Exception e) {
            log.warn("Unable to ensure sync_log table exists: {}", e.getMessage());
        }
    }

    public void record(String sourceDb, String targetDb, String tableName, String operationType,
                       String status, String message) {
        SyncLog entry = new SyncLog(null, sourceDb, targetDb, tableName, operationType, status,
                LocalDateTime.now(), message);
        inMemoryLogs.add(0, entry);
        while (inMemoryLogs.size() > 500) {
            inMemoryLogs.remove(inMemoryLogs.size() - 1);
        }
        try {
            dbAJdbcTemplate.update(
                    "INSERT INTO sync_log (source_db, target_db, table_name, operation_type, status, create_time, message) " +
                            "VALUES (?,?,?,?,?,?,?)",
                    sourceDb, targetDb, tableName, operationType, status,
                    Timestamp.valueOf(entry.getCreateTime()), message);
        } catch (Exception e) {
            log.warn("Failed to persist sync log entry: {}", e.getMessage());
        }
    }

    public List<SyncLog> findRecent(int limit) {
        try {
            return dbAJdbcTemplate.query(
                    "SELECT id, source_db, target_db, table_name, operation_type, status, create_time, message " +
                            "FROM sync_log ORDER BY create_time DESC FETCH FIRST ? ROWS ONLY",
                    new Object[]{limit},
                    new SyncLogRowMapper());
        } catch (Exception e) {
            log.warn("Falling back to in-memory logs because sync_log table is unavailable: {}", e.getMessage());
            int end = Math.min(limit, inMemoryLogs.size());
            return new LinkedList<>(inMemoryLogs.subList(0, end));
        }
    }

    private static class SyncLogRowMapper implements RowMapper<SyncLog> {
        @Override
        public SyncLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            SyncLog log = new SyncLog();
            log.setId(rs.getLong("id"));
            log.setSourceDb(rs.getString("source_db"));
            log.setTargetDb(rs.getString("target_db"));
            log.setTableName(rs.getString("table_name"));
            log.setOperationType(rs.getString("operation_type"));
            log.setStatus(rs.getString("status"));
            Timestamp timestamp = rs.getTimestamp("create_time");
            if (timestamp != null) {
                log.setCreateTime(timestamp.toLocalDateTime());
            }
            log.setMessage(rs.getString("message"));
            return log;
        }
    }
}
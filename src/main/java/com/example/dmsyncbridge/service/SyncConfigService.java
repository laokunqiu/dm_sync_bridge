package com.example.dmsyncbridge.service;

import com.example.dmsyncbridge.entity.SyncConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SyncConfigService {

    private final Map<Long, SyncConfig> configsById = new ConcurrentHashMap<>();
    private final Map<String, SyncConfig> configsByTable = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public List<SyncConfig> findAll() {
        Collection<SyncConfig> values = configsById.values();
        List<SyncConfig> result = new ArrayList<>(values);
        result.sort((a, b) -> a.getTableName().compareToIgnoreCase(b.getTableName()));
        return Collections.unmodifiableList(result);
    }

    public Optional<SyncConfig> findByTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(configsByTable.get(tableName.toLowerCase()));
    }

    public SyncConfig create(SyncConfig config) {
        validateConfig(config);
        String key = config.getTableName().toLowerCase();
        if (configsByTable.containsKey(key)) {
            throw new IllegalArgumentException("Table already configured: " + config.getTableName());
        }
        long id = idGenerator.getAndIncrement();
        config.setId(id);
        configsById.put(id, config);
        configsByTable.put(key, config);
        return config;
    }

    public void deleteByTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return;
        }
        String key = tableName.toLowerCase();
        SyncConfig removed = configsByTable.remove(key);
        if (removed != null) {
            configsById.remove(removed.getId());
        }
    }

    public void updateLastSyncTime(String tableName, Instant instant) {
        findByTableName(tableName).ifPresent(config -> config.setLastSyncTime(instant));
    }

    private void validateConfig(SyncConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (!StringUtils.hasText(config.getTableName())) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (!StringUtils.hasText(config.getPrimaryKey())) {
            throw new IllegalArgumentException("Primary key is required");
        }
    }
}
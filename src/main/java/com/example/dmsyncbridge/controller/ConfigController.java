package com.example.dmsyncbridge.controller;

import com.example.dmsyncbridge.entity.SyncConfig;
import com.example.dmsyncbridge.service.SyncConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/config/tables")
public class ConfigController {

    private final SyncConfigService syncConfigService;

    public ConfigController(SyncConfigService syncConfigService) {
        this.syncConfigService = syncConfigService;
    }

    @GetMapping
    public ResponseEntity<List<SyncConfig>> listTables() {
        return ResponseEntity.ok(syncConfigService.findAll());
    }

    @PostMapping
    public ResponseEntity<SyncConfig> addTable(@RequestBody SyncConfig config) {
        SyncConfig created = syncConfigService.create(config);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{tableName}")
    public ResponseEntity<Void> deleteTable(@PathVariable String tableName) {
        syncConfigService.deleteByTableName(tableName);
        return ResponseEntity.noContent().build();
    }
}
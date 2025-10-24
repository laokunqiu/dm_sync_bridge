package com.example.dmsyncbridge.controller;

import com.example.dmsyncbridge.entity.SyncLog;
import com.example.dmsyncbridge.service.DmSyncService;
import com.example.dmsyncbridge.service.SyncLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class SyncController {

    private final DmSyncService dmSyncService;
    private final SyncLogService syncLogService;

    public SyncController(DmSyncService dmSyncService, SyncLogService syncLogService) {
        this.dmSyncService = dmSyncService;
        this.syncLogService = syncLogService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("dbA", dmSyncService.isDbAAvailable());
        status.put("dbB", dmSyncService.isDbBAvailable());
        status.put("pendingToDbA", dmSyncService.getPendingOperationCount("dbA"));
        status.put("pendingToDbB", dmSyncService.getPendingOperationCount("dbB"));
        return ResponseEntity.ok(status);
    }

    @PostMapping("/sync/trigger")
    public ResponseEntity<Void> triggerSync(@RequestBody(required = false) SyncTriggerRequest request) {
        if (request == null || request.getTableNames().isEmpty()) {
            dmSyncService.synchronizeAll();
        } else {
            dmSyncService.synchronizeTables(request.getTableNames());
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/sync/logs")
    public ResponseEntity<List<SyncLog>> recentLogs(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<SyncLog> logs = syncLogService.findRecent(Math.max(1, Math.min(500, limit)));
        return ResponseEntity.ok(logs);
    }
}
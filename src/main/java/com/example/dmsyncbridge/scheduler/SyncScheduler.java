package com.example.dmsyncbridge.scheduler;

import com.example.dmsyncbridge.service.DmSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dm.sync.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final DmSyncService dmSyncService;

    public SyncScheduler(DmSyncService dmSyncService) {
        this.dmSyncService = dmSyncService;
    }

    @Scheduled(fixedDelayString = "${dm.sync.interval:10000}")
    public void runSync() {
        log.debug("Running scheduled synchronization");
        dmSyncService.synchronizeAll();
    }
}
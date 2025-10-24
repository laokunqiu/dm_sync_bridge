# Synchronization Principles

The `dm_sync_bridge` middleware keeps two Dameng DM8 databases consistent by applying the following principles.

## 1. Configuration Driven
- Each table participating in replication must have a `SyncConfig` entry specifying the primary key, optional included columns, and an optional `lastUpdateColumn` for incremental polling.
- Inactive configurations are ignored by both the scheduler and manual triggers.

## 2. Change Detection
1. **Snapshot window** – For every run the service fetches rows from both databases. If a `lastUpdateColumn` is present the fetch is limited to rows changed since the last successful run plus a configurable overlap (default 2 minutes) to catch late-arriving updates.
2. **Primary key comparison** – Rows are indexed by their primary key. The service detects:
    - Inserts: primary key exists in source but not in target.
    - Updates: primary key exists in both, but one or more synchronized columns differ.
    - Deletes: primary key missing from source but present in target.
3. **Conflict resolution** – When the same primary key is updated on both sides within the same window, the version with the latest `lastUpdateColumn` wins. If the column is absent, the change with the most recent detection time wins.

## 3. Change Application
- Inserts and updates are executed using Dameng `MERGE` statements so that multiple column changes can be applied atomically.
- Deletes are issued explicitly when the row disappears from the source snapshot.
- Statements are wrapped in transactions per table. If a transaction fails, it is retried according to the retry policy.

## 4. Reliability and Retry
- Each failed statement is placed in an in-memory queue dedicated to the source-target direction (dbA→dbB and dbB→dbA).
- The service retries each queued statement up to three times with exponential backoff (0.5s, 1s, 2s). Failures after the third attempt are logged and kept in the queue for the next scheduler cycle.
- When connectivity to the target database is restored the queue is replayed before fetching new changes.

## 5. Logging and Observability
- Every applied or attempted change emits a `SyncLog` entry capturing the source database, target database, table, operation, status, and any error messages.
- Additional operational metrics (success/failure counters, latency) can be added via Micrometer and exported to Prometheus.

## 6. Manual Intervention Support
- Operators can trigger full or partial synchronization through the `/sync/trigger` endpoint when they need to catch up after maintenance windows.
- Health status combines database connectivity checks with queue depths so operators can quickly identify outages or backlogs.

## 7. Extensibility
- The synchronization engine is designed with clear service boundaries (`SyncConfigService`, `DmSyncService`, `SyncLogService`) to allow swapping in persistent configuration stores, pluggable conflict strategies, or alternative queue implementations (e.g., persistent queues).
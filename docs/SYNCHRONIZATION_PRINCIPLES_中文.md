# 同步原理（Synchronization Principles）

`dm_sync_bridge` 中间件通过以下原则来保持两套达梦 DM8
数据库之间的数据一致性。

## 1. 配置驱动（Configuration Driven）

-   每个参与同步的表都必须在 `SyncConfig` 中有对应配置项，定义：
    -   主键字段（primary key）\
    -   可选的同步列（includeColumns）\
    -   可选的增量轮询列（lastUpdateColumn）
-   非激活（inactive）的配置项将被调度器和手动触发器忽略。

## 2. 变更检测（Change Detection）

1.  **快照窗口（Snapshot window）**\
    每次同步运行时，服务会从两端数据库中拉取数据行。\
    若配置了
    `lastUpdateColumn`，则仅查询自上次成功同步以来有变动的记录，并额外包含一个可配置的重叠窗口（默认
    2 分钟）以捕捉延迟到达的更新。

2.  **主键比对（Primary key comparison）**\
    系统根据主键索引行，检测以下变化：

    -   **插入（Insert）**：主键存在于源库，但不存在于目标库。\
    -   **更新（Update）**：主键在两端均存在，但同步列的值存在差异。\
    -   **删除（Delete）**：主键在源库缺失但在目标库存在。

3.  **冲突解决（Conflict resolution）**\
    当同一主键在两个数据库中于同一检测窗口内被同时更新时：

    -   若存在 `lastUpdateColumn`，以更新时间较新的版本为准。\
    -   若无此列，则以检测到的最新变更为准。

## 3. 变更应用（Change Application）

-   插入与更新通过达梦数据库的 `MERGE` 语句实现，确保多列变更的原子性。\
-   删除操作在源数据快照中缺失时显式执行。\
-   所有语句均以「每张表为单位」的事务进行封装。\
    若事务执行失败，将根据重试策略重新尝试。

## 4. 可靠性与重试机制（Reliability and Retry）

-   每个失败的 SQL
    操作会被放入一个**内存队列**中，队列按同步方向划分（dbA→dbB 与
    dbB→dbA）。\
-   每条语句最多重试三次，采用指数回退策略（0.5 秒、1 秒、2 秒）。\
-   若三次重试仍失败，该记录会被记录日志并保留在队列中，等待下一次调度周期重试。\
-   当目标数据库连接恢复后，系统会在抓取新变更前优先重放队列中的未完成任务。

## 5. 日志与可观测性（Logging and Observability）

-   每一条被执行或尝试执行的变更都会生成一条 `SyncLog` 日志，包含：
    -   源数据库与目标数据库名称\
    -   表名\
    -   操作类型（operation）\
    -   执行状态与错误信息\
-   可通过 **Micrometer**
    采集额外指标（成功/失败计数、延迟等），并导出至 **Prometheus**
    进行监控。

## 6. 手动干预支持（Manual Intervention Support）

-   运维人员可通过 `/sync/trigger`
    接口触发全量或部分同步，用于在维护窗口后快速追平数据。\
-   健康检查接口（Health）结合数据库连通性与队列深度信息，帮助快速定位故障或积压。

## 7. 可扩展性（Extensibility）

-   同步引擎采用清晰的服务边界设计：
    -   `SyncConfigService`（配置管理）\
    -   `DmSyncService`（同步核心）\
    -   `SyncLogService`（日志管理）
-   该架构允许灵活替换：
    -   持久化配置存储\
    -   可插拔的冲突解决策略\
    -   不同的队列实现（如持久化队列）

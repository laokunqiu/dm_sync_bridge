# dm_sync_bridge API 参考文档

本文档描述了 `dm_sync_bridge` 服务所暴露的 REST 接口。

## 表配置接口（Table Configuration Endpoints）

### **GET `/config/tables`**

获取所有同步配置。

-   **响应 200**

    ``` json
    [
      {
        "id": 1,
        "tableName": "person",
        "primaryKey": "id",
        "includeColumns": ["name", "email"],
        "lastUpdateColumn": "last_update_time",
        "activeFlag": true
      }
    ]
    ```

### **POST `/config/tables`**

创建或更新一个表的同步配置。 如果已存在相同 `tableName`
的条目，则会被替换。

-   **请求体**

    ``` json
    {
      "tableName": "orders",
      "primaryKey": "order_id",
      "includeColumns": ["customer_id", "amount", "status"],
      "lastUpdateColumn": "updated_at",
      "activeFlag": true
    }
    ```

-   **响应 201**

    ``` json
    {
      "id": 2,
      "tableName": "orders",
      "primaryKey": "order_id",
      "includeColumns": ["customer_id", "amount", "status"],
      "lastUpdateColumn": "updated_at",
      "activeFlag": true
    }
    ```

### **DELETE `/config/tables/{tableName}`**

从同步配置中删除指定表。

-   **路径参数**
    -   `tableName` -- 要删除的表名。
-   **响应 204** -- 删除成功。
    如果该表不存在，则不执行任何操作（no-op）。

## 同步接口（Synchronization Endpoints）

### **POST `/sync/trigger`**

立即运行同步任务。 如果未提供请求体，则默认同步所有配置的表。

-   **请求体（可选）**

    ``` json
    {
      "tableNames": ["person", "orders"]
    }
    ```

-   **响应 202**

    ``` json
    {
      "status": "STARTED",
      "tableCount": 2
    }
    ```

### **GET `/sync/logs`**

列出最近的同步日志记录。

-   **查询参数**

    -   `limit`（可选，默认值为 `50`）--- 返回的日志条目数量。

-   **响应 200**

    ``` json
    [
      {
        "id": 15,
        "sourceDb": "dbA",
        "targetDb": "dbB",
        "tableName": "person",
        "operationType": "UPDATE",
        "status": "SUCCESS",
        "createTime": "2024-05-24T10:12:45Z",
        "message": "Applied update for primary key 42"
      }
    ]
    ```

## 健康检查接口（Health Endpoint）

### **GET `/health`**

返回同步桥接服务的运行状态。

-   **响应 200**

    ``` json
    {
      "dbA": {
        "status": "UP"
      },
      "dbB": {
        "status": "UP"
      },
      "pendingQueues": {
        "dbAtoB": 0,
        "dbBtoA": 1
      }
    }
    ```

-   **响应 503** 当任一数据库无法连接时返回。
    响应体中会包含出错的连接及错误信息。

## 错误处理（Error Handling）

错误响应以结构化 JSON 格式返回，包括错误信息及可选详情，例如：

``` json
{
  "timestamp": "2024-05-24T10:11:19Z",
  "status": 400,
  "error": "Bad Request",
  "message": "tableName must not be blank",
  "path": "/config/tables"
}
```

## 认证（Authentication）

目前所有接口均 **未启用安全认证**。 若部署环境需要认证，可将服务部署在
**API 网关** 或 **反向代理** 之后，由其负责访问控制。

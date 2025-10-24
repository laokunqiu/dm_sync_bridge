# dm_sync_bridge API Reference

This document describes the REST interfaces exposed by the `dm_sync_bridge` service.

## Table Configuration Endpoints

### GET `/config/tables`
Retrieve all synchronization configurations.

- **Response 200**
  ```json
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

### POST `/config/tables`
Create or update a table configuration. If an entry with the same `tableName` already exists it is replaced.

- **Request Body**
  ```json
  {
    "tableName": "orders",
    "primaryKey": "order_id",
    "includeColumns": ["customer_id", "amount", "status"],
    "lastUpdateColumn": "updated_at",
    "activeFlag": true
  }
  ```
- **Response 201**
  ```json
  {
    "id": 2,
    "tableName": "orders",
    "primaryKey": "order_id",
    "includeColumns": ["customer_id", "amount", "status"],
    "lastUpdateColumn": "updated_at",
    "activeFlag": true
  }
  ```

### DELETE `/config/tables/{tableName}`
Remove a table from synchronization.

- **Path Parameter**
    - `tableName` – name of the table definition to remove.
- **Response 204** – configuration deleted. If the table does not exist, the operation is a no-op.

## Synchronization Endpoints

### POST `/sync/trigger`
Run the synchronization workflow immediately. When called without a body, all configured tables are processed.

- **Request Body (optional)**
  ```json
  {
    "tableNames": ["person", "orders"]
  }
  ```
- **Response 202**
  ```json
  {
    "status": "STARTED",
    "tableCount": 2
  }
  ```

### GET `/sync/logs`
List recent synchronization log entries.

- **Query Parameters**
    - `limit` (optional, default `50`) – number of records to return.
- **Response 200**
  ```json
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

## Health Endpoint

### GET `/health`
Report the status of the bridge.

- **Response 200**
  ```json
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

- **Response 503** – returned when either database is unreachable. The response body includes the failing connection and error message.

## Error Handling

Errors return structured JSON bodies with a message and optional details. Examples:

```json
{
  "timestamp": "2024-05-24T10:11:19Z",
  "status": 400,
  "error": "Bad Request",
  "message": "tableName must not be blank",
  "path": "/config/tables"
}
```

## Authentication

All endpoints are currently unsecured. If your environment requires authentication, place the service behind an API gateway or a reverse proxy that enforces access control.
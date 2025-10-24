-- Sample Dameng DM8 DDL for synchronization metadata
CREATE TABLE IF NOT EXISTS sync_config (
    id IDENTITY PRIMARY KEY,
    table_name VARCHAR(128) NOT NULL,
    primary_key VARCHAR(64) NOT NULL,
    include_columns VARCHAR(4000),
    last_update_column VARCHAR(64),
    active_flag CHAR(1) DEFAULT 'Y'
    );

CREATE UNIQUE INDEX ux_sync_config_table ON sync_config(table_name);

CREATE TABLE IF NOT EXISTS sync_log (
    id IDENTITY PRIMARY KEY,
    source_db VARCHAR(64),
    target_db VARCHAR(64),
    table_name VARCHAR(128),
    operation_type VARCHAR(32),
    status VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(4000)
    );

CREATE INDEX ix_sync_log_time ON sync_log(create_time DESC);
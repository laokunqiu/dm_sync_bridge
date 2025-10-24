package com.example.dmsyncbridge.service;

import com.example.dmsyncbridge.entity.SyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DmSyncServiceTest {

    private EmbeddedDatabase dbA;
    private EmbeddedDatabase dbB;
    private JdbcTemplate jdbcA;
    private JdbcTemplate jdbcB;
    private SyncConfigService configService;
    private SyncLogService logService;
    private DmSyncService dmSyncService;

    @BeforeEach
    void setUp() {
        dbA = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        dbB = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcA = new JdbcTemplate(dbA);
        jdbcB = new JdbcTemplate(dbB);
        logService = new SyncLogService(jdbcA);
        logService.ensureTableExists();
        configService = new SyncConfigService();
        dmSyncService = new DmSyncService(jdbcA, jdbcB, dbA, dbB, configService, logService);

        jdbcA.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), last_update TIMESTAMP)");
        jdbcB.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100), last_update TIMESTAMP)");
    }

    @AfterEach
    void tearDown() {
        dbA.shutdown();
        dbB.shutdown();
    }

    @Test
    void detectsDifferencesAndAppliesChanges() {
        jdbcA.update("INSERT INTO person (id, name, email, last_update) VALUES (?,?,?,?)",
                1, "Alice", "alice@demo", Timestamp.from(Instant.now()));
        jdbcA.update("INSERT INTO person (id, name, email, last_update) VALUES (?,?,?,?)",
                2, "Bob", "bob@demo", Timestamp.from(Instant.now()));

        jdbcB.update("INSERT INTO person (id, name, email, last_update) VALUES (?,?,?,?)",
                1, "Alice", "alice@old", Timestamp.from(Instant.now().minusSeconds(60)));
        jdbcB.update("DELETE FROM person WHERE id = 2");

        SyncConfig config = new SyncConfig();
        config.setTableName("person");
        config.setPrimaryKey("id");
        config.setIncludeColumns(java.util.Arrays.asList("name", "email", "last_update"));
        config.setLastUpdateColumn("last_update");
        config.setActiveFlag(true);
        configService.create(config);

        dmSyncService.synchronizeAll();

        List<String> emailsA = jdbcA.query("SELECT email FROM person ORDER BY id", (rs, rowNum) -> rs.getString(1));
        List<String> emailsB = jdbcB.query("SELECT email FROM person ORDER BY id", (rs, rowNum) -> rs.getString(1));

        assertThat(emailsA).containsExactly("alice@demo", "bob@demo");
        assertThat(emailsB).containsExactly("alice@demo", "bob@demo");
    }

    @Test
    void replaysOperationsAfterOutage() {
        jdbcA.update("INSERT INTO person (id, name, email, last_update) VALUES (?,?,?,?)",
                1, "Alice", "alice@demo", Timestamp.from(Instant.now()));

        ToggleableDataSource toggleable = new ToggleableDataSource(dbB);
        toggleable.setOnline(false);
        JdbcTemplate offlineTemplate = new JdbcTemplate(toggleable);
        dmSyncService = new DmSyncService(jdbcA, offlineTemplate, dbA, toggleable, configService, logService);

        SyncConfig config = new SyncConfig();
        config.setTableName("person");
        config.setPrimaryKey("id");
        config.setIncludeColumns(java.util.Arrays.asList("name", "email", "last_update"));
        config.setLastUpdateColumn("last_update");
        config.setActiveFlag(true);
        configService.create(config);

        dmSyncService.synchronizeAll();
        assertThat(dmSyncService.getPendingOperationCount("dbB")).isGreaterThan(0);
        assertThat(jdbcB.queryForList("SELECT COUNT(*) AS c FROM person", Integer.class)).containsExactly(0);

        toggleable.setOnline(true);
        dmSyncService.synchronizeAll();

        Integer count = jdbcB.queryForObject("SELECT COUNT(*) FROM person", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(dmSyncService.getPendingOperationCount("dbB")).isEqualTo(0);
    }

    private static class ToggleableDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {
        private final DataSource delegate;
        private final AtomicBoolean online = new AtomicBoolean(true);

        private ToggleableDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        public void setOnline(boolean online) {
            this.online.set(online);
        }

        @Override
        public java.sql.Connection getConnection() throws SQLException {
            if (!online.get()) {
                throw new SQLException("Database offline");
            }
            return delegate.getConnection();
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) throws SQLException {
            if (!online.get()) {
                throw new SQLException("Database offline");
            }
            return delegate.getConnection(username, password);
        }
    }
}
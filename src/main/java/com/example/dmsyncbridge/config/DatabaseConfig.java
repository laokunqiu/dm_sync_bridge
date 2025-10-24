package com.example.dmsyncbridge.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties({DatabaseConfig.DbAProperties.class, DatabaseConfig.DbBProperties.class})
public class DatabaseConfig {

    @Bean(name = "dbADataSource")
    public DataSource dbADataSource(DbAProperties properties) {
        return createDataSource(properties);
    }

    @Bean(name = "dbBDataSource")
    public DataSource dbBDataSource(DbBProperties properties) {
        return createDataSource(properties);
    }

    @Bean(name = "dbAJdbcTemplate")
    public JdbcTemplate dbAJdbcTemplate(@Qualifier("dbADataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "dbBJdbcTemplate")
    public JdbcTemplate dbBJdbcTemplate(@Qualifier("dbBDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private DataSource createDataSource(DatabaseProperties properties) {
        return DataSourceBuilder.create()
                .driverClassName(properties.getDriverClassName())
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    public interface DatabaseProperties {
        String getUrl();

        String getUsername();

        String getPassword();

        String getDriverClassName();
    }

    @ConfigurationProperties(prefix = "dm.databases.db-a")
    public static class DbAProperties implements DatabaseProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        @Override
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }

    @ConfigurationProperties(prefix = "dm.databases.db-b")
    public static class DbBProperties implements DatabaseProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        @Override
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }
}
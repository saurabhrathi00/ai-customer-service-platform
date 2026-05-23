package com.aiassistant.aiconversation.configuration;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final SecretsConfiguration secretsConfiguration;
    private final ServiceConfiguration serviceConfiguration;

    @Bean
    public DataSource dataSource() {
        SecretsConfiguration.Datasource db = secretsConfiguration.getDatasource();
        ServiceConfiguration.BusinessDb cfg = serviceConfiguration.getBusinessDb();

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(cfg.getUrl()
                + cfg.getName()
                + "?sslmode=require"
                + "&currentSchema=" + cfg.getSchema());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getPassword());
        ds.setDriverClassName(db.getDriverClassName());

        ServiceConfiguration.Pool pool = cfg.getPool();
        if (pool != null) {
            ds.setMaximumPoolSize(pool.getMaximumPoolSize());
            ds.setMinimumIdle(pool.getMinimumIdle());
            ds.setMaxLifetime(pool.getMaxLifetimeMs());
            ds.setIdleTimeout(pool.getIdleTimeoutMs());
            ds.setKeepaliveTime(pool.getKeepaliveTimeMs());
            if (pool.getConnectionTestQuery() != null && !pool.getConnectionTestQuery().isBlank()) {
                ds.setConnectionTestQuery(pool.getConnectionTestQuery());
            }
        }
        return ds;
    }
}

package com.aiassistant.subscription.configuration;

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
        ServiceConfiguration.SubscriptionDb subDb = serviceConfiguration.getSubscriptionDb();

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(subDb.getUrl()
                + subDb.getName()
                + "?sslmode=require"
                + "&currentSchema=" + subDb.getSchema());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getPassword());
        ds.setDriverClassName(db.getDriverClassName());

        ServiceConfiguration.Pool pool = subDb.getPool();
        ds.setMaximumPoolSize(pool.getMaximumPoolSize());
        ds.setMinimumIdle(pool.getMinimumIdle());
        ds.setMaxLifetime(pool.getMaxLifetimeMs());
        ds.setIdleTimeout(pool.getIdleTimeoutMs());
        ds.setKeepaliveTime(pool.getKeepaliveTimeMs());
        return ds;
    }
}

package com.aiassistant.callorchestration.configuration;

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

        HikariDataSource ds = new HikariDataSource();
        ServiceConfiguration.BusinessDb businessDb = serviceConfiguration.getBusinessDb();
        ds.setJdbcUrl(businessDb.getUrl()
                + businessDb.getName()
                + "?sslmode=require"
                + "&currentSchema=" + businessDb.getSchema());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getPassword());
        ds.setDriverClassName(db.getDriverClassName());
        ServiceConfiguration.Pool pool = businessDb.getPool();
        ds.setMaximumPoolSize(pool.getMaximumPoolSize());
        ds.setMinimumIdle(pool.getMinimumIdle());
        ds.setMaxLifetime(pool.getMaxLifetimeMs());
        ds.setIdleTimeout(pool.getIdleTimeoutMs());
        ds.setKeepaliveTime(pool.getKeepaliveTimeMs());
        ds.setConnectionTestQuery(pool.getConnectionTestQuery());
        return ds;
    }
}

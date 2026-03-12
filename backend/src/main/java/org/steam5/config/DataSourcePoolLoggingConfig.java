package org.steam5.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class DataSourcePoolLoggingConfig {

    @Bean
    public ApplicationRunner logEffectiveDataSourcePool(DataSource dataSource) {
        return args -> {
            if (dataSource instanceof HikariDataSource hikari) {
                log.info(
                        "Datasource pool active: type=HikariDataSource, poolName={}, maxPoolSize={}, minIdle={}, connectionTimeoutMs={}, idleTimeoutMs={}, maxLifetimeMs={}, validationTimeoutMs={}",
                        hikari.getPoolName(),
                        hikari.getMaximumPoolSize(),
                        hikari.getMinimumIdle(),
                        hikari.getConnectionTimeout(),
                        hikari.getIdleTimeout(),
                        hikari.getMaxLifetime(),
                        hikari.getValidationTimeout()
                );
            } else {
                log.warn("Datasource pool type is not HikariDataSource: {}", dataSource.getClass().getName());
            }
        };
    }
}


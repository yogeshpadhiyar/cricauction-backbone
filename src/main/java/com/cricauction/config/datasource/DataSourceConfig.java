package com.cricauction.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise Master-Replica Database Configuration for High-Concurrency Scaling.
 *
 * <p>Key Architecture Highlights:
 * <ul>
 *   <li><b>Master Pool:</b> Handles 100% of mutations (INSERT, UPDATE, DELETE) and DDL migrations.</li>
 *   <li><b>Replica Pool:</b> Handles 100% of read operations flagged with {@code @Transactional(readOnly = true)}.
 *       Connections are marked read-only at the pool/JDBC level for optimal read performance and server-side safety.</li>
 *   <li><b>Dynamic Routing:</b> {@link RoutingDataSource} inspects Spring's transaction synchronization state.</li>
 *   <li><b>Lazy Connection Proxy:</b> {@link LazyConnectionDataSourceProxy} defers physical JDBC connection acquisition
 *       from HikariCP until the first query statement is actually executed. This ensures transaction read-only attributes
 *       are bound before the routing key is determined.</li>
 *   <li><b>Flyway Migration Safety:</b> Flyway is bound specifically to {@code masterDataSource} via {@link FlywayDataSource},
 *       preventing migration scripts from ever hitting a read replica.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final Environment env;

    @Bean(name = "masterDataSource")
    @FlywayDataSource
    public DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();

        // 1. Bind common datasource and hikari properties
        Binder.get(env).bind("spring.datasource.hikari", Bindable.ofInstance(config));

        // 2. Resolve master connection parameters
        String url = env.getProperty("spring.datasource.master.url",
                env.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/cricauction"));
        String username = env.getProperty("spring.datasource.master.username",
                env.getProperty("spring.datasource.username", ""));
        String password = env.getProperty("spring.datasource.master.password",
                env.getProperty("spring.datasource.password", ""));
        String driver = env.getProperty("spring.datasource.master.driver-class-name",
                env.getProperty("spring.datasource.driver-class-name", "org.postgresql.Driver"));

        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driver);
        config.setPoolName("CricAuction-Master-Pool");
        config.setReadOnly(false);

        // Bind master-specific overrides if configured
        Binder.get(env).bind("spring.datasource.master.hikari", Bindable.ofInstance(config));

        log.info("[DB-CONFIG] Master DataSource configured for URL: {} (Pool: {})", url, config.getPoolName());
        return new HikariDataSource(config);
    }

    @Bean(name = "replicaDataSource")
    public DataSource replicaDataSource() {
        HikariConfig config = new HikariConfig();

        // 1. Bind common datasource and hikari properties
        Binder.get(env).bind("spring.datasource.hikari", Bindable.ofInstance(config));

        // 2. Resolve replica connection parameters (fallback to master if replica URL is not configured)
        String masterUrl = env.getProperty("spring.datasource.master.url",
                env.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/cricauction"));
        String masterUser = env.getProperty("spring.datasource.master.username",
                env.getProperty("spring.datasource.username", ""));
        String masterPass = env.getProperty("spring.datasource.master.password",
                env.getProperty("spring.datasource.password", ""));
        String masterDriver = env.getProperty("spring.datasource.master.driver-class-name",
                env.getProperty("spring.datasource.driver-class-name", "org.postgresql.Driver"));

        String replicaUrl = env.getProperty("spring.datasource.replica.url", masterUrl);
        String replicaUser = env.getProperty("spring.datasource.replica.username", masterUser);
        String replicaPass = env.getProperty("spring.datasource.replica.password", masterPass);
        String replicaDriver = env.getProperty("spring.datasource.replica.driver-class-name", masterDriver);

        config.setJdbcUrl(replicaUrl);
        config.setUsername(replicaUser);
        config.setPassword(replicaPass);
        config.setDriverClassName(replicaDriver);
        config.setPoolName("CricAuction-Replica-Pool");
        config.setReadOnly(true);

        // Bind replica-specific overrides if configured
        Binder.get(env).bind("spring.datasource.replica.hikari", Bindable.ofInstance(config));

        log.info("[DB-CONFIG] Replica DataSource configured for URL: {} (Pool: {}, ReadOnly: true)", replicaUrl, config.getPoolName());
        return new HikariDataSource(config);
    }

    @Bean(name = "routingDataSource")
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("replicaDataSource") DataSource replicaDataSource) {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, masterDataSource);
        targetDataSources.put(DataSourceType.REPLICA, replicaDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.afterPropertiesSet();

        log.info("[DB-CONFIG] Routing DataSource configured with Master and Replica targets");
        return routingDataSource;
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        log.info("[DB-CONFIG] LazyConnectionDataSourceProxy initialized as @Primary DataSource");
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}

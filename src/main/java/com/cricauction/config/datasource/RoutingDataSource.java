package com.cricauction.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dynamic Routing DataSource that determines whether to lease a connection from
 * the MASTER (write) pool or the REPLICA (read-only) pool.
 *
 * <p>Routing Decision Flow:
 * 1. Explicit override in {@link RoutingDataSourceContextHolder} takes top precedence.
 * 2. If Spring {@link TransactionSynchronizationManager#isCurrentTransactionReadOnly()} is true,
 *    route to {@link DataSourceType#REPLICA}.
 * 3. Otherwise (write transaction or non-transactional execution), route to {@link DataSourceType#MASTER}.
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    private static volatile DataSourceType lastSelectedDataSourceType = DataSourceType.MASTER;

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType explicitType = RoutingDataSourceContextHolder.getDataSourceType();
        if (explicitType != null) {
            log.debug("[DB-ROUTING] Explicit routing override detected -> {}", explicitType);
            lastSelectedDataSourceType = explicitType;
            return explicitType;
        }

        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        DataSourceType target = isReadOnly ? DataSourceType.REPLICA : DataSourceType.MASTER;
        log.debug("[DB-ROUTING] Dynamic route selected -> {} (Transaction isReadOnly: {})", target, isReadOnly);
        lastSelectedDataSourceType = target;
        return target;
    }

    /**
     * Helper accessor for integration tests, observability, and health metrics.
     */
    public static DataSourceType getLastSelectedDataSourceType() {
        return lastSelectedDataSourceType;
    }
}

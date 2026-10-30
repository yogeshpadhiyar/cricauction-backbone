package com.cricauction.config.datasource;

/**
 * Thread-local context holder to manually specify or override the target database routing key.
 * Primary routing is determined automatically via @Transactional(readOnly = true), but this holder
 * allows explicit manual control when needed (e.g. read-your-own-writes or immediate post-mutation reads).
 */
public final class RoutingDataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    private RoutingDataSourceContextHolder() {}

    public static void setDataSourceType(DataSourceType dataSourceType) {
        CONTEXT.set(dataSourceType);
    }

    public static DataSourceType getDataSourceType() {
        return CONTEXT.get();
    }

    public static void clearDataSourceType() {
        CONTEXT.remove();
    }
}

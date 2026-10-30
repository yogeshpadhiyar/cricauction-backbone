package com.cricauction.config.datasource;

import com.cricauction.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Master-Replica Read/Write DataSource Routing Verification")
class DataSourceRoutingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("masterDataSource")
    private DataSource masterDataSource;

    @Autowired
    @Qualifier("replicaDataSource")
    private DataSource replicaDataSource;

    @Autowired
    @Qualifier("routingDataSource")
    private DataSource routingDataSource;

    @Autowired
    private DataSource primaryDataSource;

    @AfterEach
    void tearDown() {
        RoutingDataSourceContextHolder.clearDataSourceType();
    }

    @Test
    @DisplayName("Should wire and initialize Master, Replica, and Lazy Routing DataSources")
    void testDataSourceBeansInitialization() {
        assertThat(masterDataSource).isNotNull();
        assertThat(replicaDataSource).isNotNull();
        assertThat(routingDataSource).isNotNull();
        assertThat(primaryDataSource).isNotNull();
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Should route @Transactional(readOnly = true) queries to REPLICA DataSource")
    void testReadOnlyTransactionRoutesToReplica() {
        userRepository.count();
        assertThat(RoutingDataSource.getLastSelectedDataSourceType()).isEqualTo(DataSourceType.REPLICA);
    }

    @Test
    @Transactional
    @DisplayName("Should route @Transactional(readOnly = false) write operations to MASTER DataSource")
    void testWriteTransactionRoutesToMaster() {
        userRepository.count();
        assertThat(RoutingDataSource.getLastSelectedDataSourceType()).isEqualTo(DataSourceType.MASTER);
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Should honor explicit RoutingDataSourceContextHolder manual override even in read-only transaction")
    void testExplicitMasterOverrideInReadOnlyTransaction() {
        RoutingDataSourceContextHolder.setDataSourceType(DataSourceType.MASTER);
        userRepository.count();
        assertThat(RoutingDataSource.getLastSelectedDataSourceType()).isEqualTo(DataSourceType.MASTER);
    }

    @Test
    @Transactional
    @DisplayName("Should honor explicit RoutingDataSourceContextHolder manual override even in write transaction")
    void testExplicitReplicaOverrideInWriteTransaction() {
        RoutingDataSourceContextHolder.setDataSourceType(DataSourceType.REPLICA);
        userRepository.count();
        assertThat(RoutingDataSource.getLastSelectedDataSourceType()).isEqualTo(DataSourceType.REPLICA);
    }
}

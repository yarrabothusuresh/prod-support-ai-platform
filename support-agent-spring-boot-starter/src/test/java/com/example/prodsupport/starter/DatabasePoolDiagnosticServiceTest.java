package com.example.prodsupport.starter;

import com.example.prodsupport.starter.model.DatabasePoolDiagnostics;
import com.example.prodsupport.starter.properties.SupportProperties;
import com.example.prodsupport.starter.service.DatabasePoolDiagnosticService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabasePoolDiagnosticServiceTest {

    @Test
    @DisplayName("Returns unavailable when database diagnostics are disabled")
    void testDisabledDiagnostics() {
        SupportProperties props = new SupportProperties();
        props.getDiagnostics().getDatabase().setEnabled(false);

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);

        DatabasePoolDiagnosticService service = new DatabasePoolDiagnosticService(props, provider);
        DatabasePoolDiagnostics result = service.checkPool();

        assertEquals("UNKNOWN", result.status());
        assertTrue(result.message().contains("disabled"));
    }

    @Test
    @DisplayName("Returns unavailable when no DataSource bean is available")
    void testNoDataSource() {
        SupportProperties props = new SupportProperties();
        props.getDiagnostics().getDatabase().setEnabled(true);
        props.getDiagnostics().getDatabase().getPool().setEnabled(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        DatabasePoolDiagnosticService service = new DatabasePoolDiagnosticService(props, provider);
        DatabasePoolDiagnostics result = service.checkPool();

        assertEquals("UNKNOWN", result.status());
        assertTrue(result.message().contains("No DataSource"));
    }

    @Test
    @DisplayName("Calculates utilization and NORMAL status correctly for Hikari pool")
    void testNormalHikariPool() {
        SupportProperties props = new SupportProperties();
        props.getDiagnostics().getDatabase().setEnabled(true);
        props.getDiagnostics().getDatabase().getPool().setEnabled(true);
        props.getDiagnostics().getDatabase().getPool().setWarningUtilizationPercent(80);
        props.getDiagnostics().getDatabase().getPool().setCriticalUtilizationPercent(95);

        HikariDataSource ds = mock(HikariDataSource.class);
        HikariPoolMXBean mxBean = mock(HikariPoolMXBean.class);

        when(ds.getPoolName()).thenReturn("PaymentHikariPool");
        when(ds.getMaximumPoolSize()).thenReturn(10);
        when(ds.getMinimumIdle()).thenReturn(2);
        when(ds.getHikariPoolMXBean()).thenReturn(mxBean);

        when(mxBean.getActiveConnections()).thenReturn(4);
        when(mxBean.getIdleConnections()).thenReturn(6);
        when(mxBean.getTotalConnections()).thenReturn(10);
        when(mxBean.getThreadsAwaitingConnection()).thenReturn(0);

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ds);

        DatabasePoolDiagnosticService service = new DatabasePoolDiagnosticService(props, provider);
        DatabasePoolDiagnostics result = service.checkPool();

        assertEquals("PaymentHikariPool", result.poolName());
        assertEquals(4, result.activeConnections());
        assertEquals(10, result.maxPoolSize());
        assertEquals(40, result.utilizationPercent());
        assertEquals(0, result.threadsAwaitingConnection());
        assertEquals("NORMAL", result.status());
    }

    @Test
    @DisplayName("Classifies WARNING when utilization >= 80%")
    void testWarningHikariPool() {
        SupportProperties props = new SupportProperties();
        props.getDiagnostics().getDatabase().setEnabled(true);
        props.getDiagnostics().getDatabase().getPool().setEnabled(true);

        HikariDataSource ds = mock(HikariDataSource.class);
        HikariPoolMXBean mxBean = mock(HikariPoolMXBean.class);

        when(ds.getPoolName()).thenReturn("PaymentHikariPool");
        when(ds.getMaximumPoolSize()).thenReturn(10);
        when(ds.getHikariPoolMXBean()).thenReturn(mxBean);

        when(mxBean.getActiveConnections()).thenReturn(9);
        when(mxBean.getIdleConnections()).thenReturn(1);
        when(mxBean.getTotalConnections()).thenReturn(10);
        when(mxBean.getThreadsAwaitingConnection()).thenReturn(0);

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ds);

        DatabasePoolDiagnosticService service = new DatabasePoolDiagnosticService(props, provider);
        DatabasePoolDiagnostics result = service.checkPool();

        assertEquals(90, result.utilizationPercent());
        assertEquals("WARNING", result.status());
    }

    @Test
    @DisplayName("Classifies CRITICAL when utilization >= 95% or threads are waiting")
    void testCriticalHikariPool() {
        SupportProperties props = new SupportProperties();
        props.getDiagnostics().getDatabase().setEnabled(true);
        props.getDiagnostics().getDatabase().getPool().setEnabled(true);

        HikariDataSource ds = mock(HikariDataSource.class);
        HikariPoolMXBean mxBean = mock(HikariPoolMXBean.class);

        when(ds.getPoolName()).thenReturn("PaymentHikariPool");
        when(ds.getMaximumPoolSize()).thenReturn(10);
        when(ds.getHikariPoolMXBean()).thenReturn(mxBean);

        when(mxBean.getActiveConnections()).thenReturn(10);
        when(mxBean.getIdleConnections()).thenReturn(0);
        when(mxBean.getTotalConnections()).thenReturn(10);
        when(mxBean.getThreadsAwaitingConnection()).thenReturn(4);

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ds);

        DatabasePoolDiagnosticService service = new DatabasePoolDiagnosticService(props, provider);
        DatabasePoolDiagnostics result = service.checkPool();

        assertEquals(100, result.utilizationPercent());
        assertEquals(4, result.threadsAwaitingConnection());
        assertEquals("CRITICAL", result.status());
    }
}

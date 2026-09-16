package com.example.prodsupport;

import com.example.prodsupport.database.model.*;
import com.example.prodsupport.database.provider.DatabaseDiagnosticContext;
import com.example.prodsupport.database.provider.PostgreSqlDiagnosticProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PostgreSqlDiagnosticProviderTest {

    private final PostgreSqlDiagnosticProvider provider = new PostgreSqlDiagnosticProvider();

    @Test
    @DisplayName("Health check succeeds with UP when predefined SELECT 1 returns rows")
    void testHealthCheckSuccess() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT 1")).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);

        DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                "payment-service", "local", "payment-db", DatabaseType.POSTGRESQL, ds, 3000, 3
        );

        DatabaseHealthResult result = provider.checkHealth(context);

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.reachable()).isTrue();
        assertThat(result.databaseType()).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(result.responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Health check returns DOWN without leaking internal credentials on connection exception")
    void testHealthCheckConnectionFailure() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("Connection refused: password=supersecretpass"));

        DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                "payment-service", "local", "payment-db", DatabaseType.POSTGRESQL, ds, 3000, 3
        );

        DatabaseHealthResult result = provider.checkHealth(context);

        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.reachable()).isFalse();
        assertThat(result.message()).doesNotContain("supersecretpass");
        assertThat(result.message()).contains("password=***");
    }

    @Test
    @DisplayName("Activity check returns aggregate session and long-running metrics")
    void testActivityCheckSuccess() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        ResultSet rs1 = mock(ResultSet.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        ResultSet rs2 = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(conn);

        // Aggregate session query
        when(conn.prepareStatement(contains("active_count"))).thenReturn(stmt1);
        when(stmt1.executeQuery()).thenReturn(rs1);
        when(rs1.next()).thenReturn(true);
        when(rs1.getInt("active_count")).thenReturn(5);
        when(rs1.getInt("idle_count")).thenReturn(2);
        when(rs1.getInt("idle_in_trans_count")).thenReturn(1);
        when(rs1.getInt("waiting_count")).thenReturn(2);

        // Long running query
        when(conn.prepareStatement(contains("long_count"))).thenReturn(stmt2);
        when(stmt2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true);
        when(rs2.getInt("long_count")).thenReturn(1);
        when(rs2.getDouble("oldest_duration_ms")).thenReturn(7500.0);

        DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                "payment-service", "local", "payment-db", DatabaseType.POSTGRESQL, ds, 3000, 3
        );

        DatabaseActivityResult result = provider.checkActivity(context);

        assertThat(result.activeSessions()).isEqualTo(5);
        assertThat(result.idleSessions()).isEqualTo(2);
        assertThat(result.idleInTransactionSessions()).isEqualTo(1);
        assertThat(result.waitingSessions()).isEqualTo(2);
        assertThat(result.longRunningQueryCount()).isEqualTo(1);
        assertThat(result.oldestDurationMs()).isEqualTo(7500);
    }

    @Test
    @DisplayName("Activity check handles permission denied gracefully with warnings")
    void testActivityCheckPermissionDenied() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(contains("active_count"))).thenReturn(stmt1);
        when(stmt1.executeQuery()).thenThrow(new SQLException("permission denied for view pg_stat_activity"));

        DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                "payment-service", "local", "payment-db", DatabaseType.POSTGRESQL, ds, 3000, 3
        );

        DatabaseActivityResult result = provider.checkActivity(context);

        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("permission denied");
    }
}

package io.backend.notifications.integration.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.integration.base.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Integration test verifying the V2 phone NOT NULL migration. */
@DisplayName("V2 Enforce Phone NOT NULL Migration")
class V2MigrationIntegrationTest extends AbstractIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("should make phone column NOT NULL after V2 migration")
  void shouldMakePhoneColumnNotNull() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      ResultSet rs =
          stmt.executeQuery(
              "SELECT is_nullable FROM information_schema.columns "
                  + "WHERE table_name = 'users' AND column_name = 'phone'");

      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("is_nullable")).isEqualTo("NO");
    }
  }

  @Test
  @DisplayName("should have no null phones in users table after V2 backfill")
  void shouldHaveNoNullPhones() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      ResultSet rs =
          stmt.executeQuery("SELECT count(*) AS null_count FROM users WHERE phone IS NULL");

      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt("null_count")).isZero();
    }
  }
}

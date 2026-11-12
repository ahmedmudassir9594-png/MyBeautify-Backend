package com.cosmetics.ecommerce;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationValidationTest {

    @Test
    @DisplayName("Validate Flyway Migration Chain on disposable MySQL-mode database")
    void testFlywayMigrations_ExecuteSuccessfully() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String password = "";

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);

        // Verify tables exist
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement()) {

            Set<String> tableNames = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tableNames.add(rs.getString(1).toLowerCase());
                }
            }

            assertThat(tableNames).contains(
                    "users", "addresses", "categories", "products", "orders",
                    "order_items", "payments", "payment_webhook_events", "combos",
                    "refunds", "refresh_tokens"
            );
        }
    }

    @Test
    @DisplayName("Validate Incremental Flyway Migration Step-by-Step: V1 -> V2 -> V3 -> V4 Upgrade Chain")
    void testIncrementalFlywayMigration_V1ToV2ToV3_ExecuteSuccessfully() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway_incremental_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String password = "";

        // Step 1: Migrate baseline V1
        Flyway flywayV1 = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .target("1")
                .load();
        var res1 = flywayV1.migrate();
        assertThat(res1.success).isTrue();
        assertThat(res1.targetSchemaVersion).isEqualTo("1");

        // Step 2: Migrate incremental V2
        Flyway flywayV2 = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .target("2")
                .load();
        var res2 = flywayV2.migrate();
        assertThat(res2.success).isTrue();
        assertThat(res2.targetSchemaVersion).isEqualTo("2");

        // Step 3: Migrate incremental V3
        Flyway flywayV3 = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .target("3")
                .load();
        var res3 = flywayV3.migrate();
        assertThat(res3.success).isTrue();
        assertThat(res3.targetSchemaVersion).isEqualTo("3");

        // Step 4: Migrate incremental V4 (Refresh Tokens)
        Flyway flywayV4 = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .target("4")
                .load();
        var res4 = flywayV4.migrate();
        assertThat(res4.success).isTrue();
        assertThat(res4.targetSchemaVersion).isEqualTo("4");

        // Step 5: Verify refunds & refresh_tokens tables exist
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement()) {

            Set<String> tableNames = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tableNames.add(rs.getString(1).toLowerCase());
                }
            }

            assertThat(tableNames).contains("refunds", "payments", "combos", "orders", "refresh_tokens");
        }
    }
}

package com.familyfund;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @Order(1)
    @DisplayName("Verify Flyway migration executes successfully")
    void runFlywayMigration() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(false)
                .load();

        flyway.repair();
        MigrateResult result = flyway.migrate();
        org.slf4j.LoggerFactory.getLogger(DatabaseMigrationTest.class).info(
                "FLYWAY MIGRATION RESULT: initial={}, target={}, executed={}, success={}",
                result.initialSchemaVersion, result.targetSchemaVersion, result.migrationsExecuted, result.success
        );
        assertTrue(result.success, "Flyway migration should succeed");
    }

    @Test
    @Order(2)
    @DisplayName("Verify database schema integrity and seeded master data")
    void verifyDatabaseSchemaAndMasterData() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            System.out.println("=== VERIFYING TABLES IN PUBLIC SCHEMA ===");
            var historyRs = stmt.executeQuery(
                    "SELECT installed_rank, version, description, script, success FROM flyway_schema_history ORDER BY installed_rank"
            );
            System.out.println("=== FLYWAY SCHEMA HISTORY ===");
            while (historyRs.next()) {
                System.out.printf("Rank %d: Version %s, Desc: %s, Script: %s, Success: %b%n",
                        historyRs.getInt("installed_rank"),
                        historyRs.getString("version"),
                        historyRs.getString("description"),
                        historyRs.getString("script"),
                        historyRs.getBoolean("success"));
            }

            var rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name != 'flyway_schema_history' " +
                    "ORDER BY table_name"
            );
            Set<String> tables = new HashSet<>();
            while (rs.next()) {
                String tableName = rs.getString("table_name");
                tables.add(tableName);
                System.out.println((tables.size()) + ". " + tableName);
            }

            // Assert domain table count and core tables existence
            assertTrue(tables.size() >= 24, "Public schema should contain at least 24 domain tables");
            assertTrue(tables.containsAll(Set.of(
                    "clans",
                    "family_branches",
                    "members",
                    "invitation_codes",
                    "system_roles",
                    "user_accounts",
                    "branch_join_requests",
                    "member_privacy_settings"
            )), "Core domain tables must be present after migrations");

            // Verify clan_id column exists on branch_join_requests (Migration V6)
            var colRs = stmt.executeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'branch_join_requests' AND column_name = 'clan_id'"
            );
            assertTrue(colRs.next(), "branch_join_requests must have clan_id column from V6 migration");

            // Verify US-08 progress warning table exists (created by V1)
            assertTrue(tables.contains("task_progress_alerts"),
                    "task_progress_alerts table (US-08 progress warnings) must be present");

            System.out.println("\n=== VERIFYING SEEDED ROLES IN system_roles ===");
            var rolesRs = stmt.executeQuery("SELECT role_id, role_name, description FROM system_roles ORDER BY role_id");
            Set<String> roles = new HashSet<>();
            while (rolesRs.next()) {
                String roleId = rolesRs.getString("role_id");
                roles.add(roleId);
                System.out.printf("[%s] %s: %s%n",
                        roleId,
                        rolesRs.getString("role_name"),
                        rolesRs.getString("description"));
            }

            // Assert system roles
            assertEquals(4, roles.size(), "system_roles must contain exactly 4 seeded roles");
            assertEquals(Set.of("ROLE_ADMIN", "ROLE_BRANCH_HEAD", "ROLE_CLAN_HEAD", "ROLE_MEMBER"), roles,
                    "Expected standard roles must be present in system_roles");
        }
    }
}

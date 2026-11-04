package com.familyfund;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
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
class DatabaseMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
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
        System.out.println("==================================================");
        System.out.println("FLYWAY MIGRATION SUCCESSFUL!");
        System.out.println("Initial schema version: " + result.initialSchemaVersion);
        System.out.println("Target schema version: " + result.targetSchemaVersion);
        System.out.println("Migrations executed: " + result.migrationsExecuted);
        System.out.println("==================================================");
        assertTrue(result.success, "Flyway migration should succeed");
    }

    @Test
    @DisplayName("Verify database schema integrity and seeded master data")
    void verifyDatabaseSchemaAndMasterData() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            System.out.println("=== VERIFYING TABLES IN PUBLIC SCHEMA ===");
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
                    "branch_join_requests"
            )), "Core domain tables must be present after migrations");

            // Verify clan_id column exists on branch_join_requests (Migration V6)
            var colRs = stmt.executeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'branch_join_requests' AND column_name = 'clan_id'"
            );
            assertTrue(colRs.next(), "branch_join_requests must have clan_id column from V6 migration");

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

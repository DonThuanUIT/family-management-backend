package com.familyfund;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void runFlywayMigration() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

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
    void verifyNeonTablesAndRoles() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            System.out.println("=== VERIFYING TABLES ON NEON DATABASE ===");
            var rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name"
            );
            int tableCount = 0;
            while (rs.next()) {
                tableCount++;
                System.out.println(tableCount + ". " + rs.getString("table_name"));
            }

            System.out.println("\n=== VERIFYING SEEDED ROLES IN system_roles ===");
            var rolesRs = stmt.executeQuery("SELECT role_id, role_name, description FROM system_roles ORDER BY role_id");
            while (rolesRs.next()) {
                System.out.printf("[%s] %s: %s%n",
                        rolesRs.getString("role_id"),
                        rolesRs.getString("role_name"),
                        rolesRs.getString("description"));
            }
        }
    }
}

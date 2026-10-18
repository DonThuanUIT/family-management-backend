package com.familyfund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(FamilyfundApiApplication.class);

    @Test
    @DisplayName("Verify Spring Modulith modular architecture boundaries and dependencies")
    void verifyModularity() {
        modules.verify();
    }

    @Test
    @DisplayName("Print detected Spring Modulith modules and their interfaces")
    void printModules() {
        modules.forEach(System.out::println);
    }
}


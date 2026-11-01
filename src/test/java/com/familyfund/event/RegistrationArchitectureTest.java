package com.familyfund.event;

import com.familyfund.FamilyfundApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture Verification Test Suite for Event Attendance Registration (US-06).
 * Uses Spring Modulith to enforce modular encapsulation, prevent architectural leaks,
 * and ensure that cross-module dependencies stay strictly within public `.api` contracts.
 */
class RegistrationArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(FamilyfundApiApplication.class);

    @Test
    @DisplayName("Spring Modulith: verify entire application architecture rules and module boundaries")
    void verifyModularity() {
        modules.verify();
    }

    @Test
    @DisplayName("Spring Modulith: verify event module encapsulates internal registration components")
    void verifyEventRegistrationModuleEncapsulation() {
        ApplicationModule eventModule = modules.getModuleByName("event")
                .orElseThrow(() -> new AssertionError("Module 'event' not detected by Spring Modulith"));

        assertThat(eventModule).isNotNull();

        // Enforce that no internal registration package is leaked as a public named interface
        boolean hasInternalExposed = eventModule.getNamedInterfaces().stream()
                .anyMatch(ni -> ni.getName().contains("internal"));

        assertThat(hasInternalExposed).isFalse();
    }

    @Test
    @DisplayName("Spring Modulith: verify event module dependencies on genealogy are strictly via public API")
    void verifyEventModuleDependsOnGenealogyApiOnly() {
        ApplicationModule eventModule = modules.getModuleByName("event")
                .orElseThrow(() -> new AssertionError("Module 'event' not detected"));

        ApplicationModule genealogyModule = modules.getModuleByName("genealogy")
                .orElseThrow(() -> new AssertionError("Module 'genealogy' not detected"));

        assertThat(eventModule).isNotNull();
        assertThat(genealogyModule).isNotNull();

        // modules.verify() already performs deep ArchUnit bytecode inspection on all module cross-references
        modules.verify();
    }
}


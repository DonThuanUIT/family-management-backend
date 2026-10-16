package com.familyfund.modules.invitation.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator("http://localhost:3000/join");

    @Test
    @DisplayName("Generated code must have length of 8 and contain only characters from ALPHABET")
    void generateCode_ShouldProduceValidFormat() {
        String code = generator.generateCode();

        assertNotNull(code);
        assertEquals(InviteCodeGenerator.CODE_LENGTH, code.length());
        for (char c : code.toCharArray()) {
            assertTrue(InviteCodeGenerator.ALPHABET.indexOf(c) >= 0,
                    "Code contains illegal character: " + c);
        }
    }

    @Test
    @DisplayName("Generating 10,000 codes should produce 10,000 unique values without collision")
    void generateCode_ShouldHaveNoCollisionsInLargeSample() {
        int sampleSize = 10_000;
        Set<String> generatedCodes = new HashSet<>(sampleSize);

        for (int i = 0; i < sampleSize; i++) {
            String code = generator.generateCode();
            generatedCodes.add(code);
        }

        assertEquals(sampleSize, generatedCodes.size(), "Collision detected in 10,000 generated codes");
    }

    @Test
    @DisplayName("Building shareable URL should format query parameter correctly")
    void buildShareableUrl_ShouldAppendCodeParameter() {
        String code = "8K9P2X7N";
        String url = generator.buildShareableUrl(code);

        assertEquals("http://localhost:3000/join?code=8K9P2X7N", url);
    }

    @Test
    @DisplayName("Building shareable URL should handle base URL with trailing slash cleanly")
    void buildShareableUrl_ShouldHandleTrailingSlash() {
        InviteCodeGenerator slashGenerator = new InviteCodeGenerator("https://familyfund.app/invite/");
        String url = slashGenerator.buildShareableUrl("ABCD2345");

        assertEquals("https://familyfund.app/invite?code=ABCD2345", url);
    }

    @Test
    @DisplayName("Building shareable URL with null or empty code should throw IllegalArgumentException")
    void buildShareableUrl_ShouldThrowOnBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> generator.buildShareableUrl(null));
        assertThrows(IllegalArgumentException.class, () -> generator.buildShareableUrl("   "));
    }
}


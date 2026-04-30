package com.example.kolla.services;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for meeting code uniqueness.
 *
 * Property 9: Meeting Code Uniqueness
 *
 * These tests verify the properties of the meeting code generation algorithm
 * used in MeetingServiceImpl.generateUniqueCode() in isolation (pure logic),
 * without requiring a Spring context or database.
 *
 * The algorithm:
 *   1. Take the first 8 characters of a UUID (without dashes), uppercased.
 *   2. If a collision is detected (existsByCode), retry up to 10 times.
 *   3. After 10 attempts, fall back to 12 characters.
 *
 * Requirements: 3.1
 */
class MeetingCodeUniquenessPropertyTest {

    // ── Code generation logic (mirrors MeetingServiceImpl) ───────────────────

    /**
     * Generates a single meeting code candidate (8 uppercase alphanumeric chars).
     */
    private String generateCodeCandidate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * Generates a fallback code (12 uppercase alphanumeric chars).
     */
    private String generateFallbackCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * Simulates the full generateUniqueCode() method given a set of already-used codes.
     */
    private String generateUniqueCode(Set<String> existingCodes) {
        String code;
        int attempts = 0;
        do {
            code = generateCodeCandidate();
            attempts++;
            if (attempts > 10) {
                code = generateFallbackCode();
                break;
            }
        } while (existingCodes.contains(code));
        return code;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /**
     * Property 9a: Format — generated codes consist only of uppercase alphanumeric characters.
     */
    @Property(tries = 500)
    @Label("P9a: Generated code contains only uppercase alphanumeric characters")
    void generatedCodeIsUppercaseAlphanumeric() {
        String code = generateCodeCandidate();
        assertThat(code)
                .as("Meeting code must be uppercase alphanumeric")
                .matches("[A-Z0-9]+");
    }

    /**
     * Property 9b: Length — standard code is exactly 8 characters.
     */
    @Property(tries = 500)
    @Label("P9b: Standard generated code is exactly 8 characters long")
    void standardCodeIsEightChars() {
        String code = generateCodeCandidate();
        assertThat(code)
                .as("Standard meeting code must be exactly 8 characters")
                .hasSize(8);
    }

    /**
     * Property 9c: Fallback length — fallback code is exactly 12 characters.
     */
    @Property(tries = 500)
    @Label("P9c: Fallback generated code is exactly 12 characters long")
    void fallbackCodeIsTwelveChars() {
        String code = generateFallbackCode();
        assertThat(code)
                .as("Fallback meeting code must be exactly 12 characters")
                .hasSize(12);
    }

    /**
     * Property 9d: Uniqueness — generating N codes in sequence produces no duplicates.
     * Uses a small batch size to keep the test fast while still exercising the property.
     */
    @Property(tries = 100)
    @Label("P9d: Batch of generated codes contains no duplicates")
    void batchOfCodesHasNoDuplicates(
            @ForAll @IntRange(min = 10, max = 100) int batchSize) {

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < batchSize; i++) {
            String code = generateUniqueCode(generated);
            assertThat(generated)
                    .as("Code '%s' must not already exist in the generated set", code)
                    .doesNotContain(code);
            generated.add(code);
        }
        assertThat(generated).hasSize(batchSize);
    }

    /**
     * Property 9e: Collision avoidance — when the first candidate collides,
     * the algorithm retries and produces a different code.
     */
    @Property(tries = 200)
    @Label("P9e: Algorithm avoids collision with pre-existing codes")
    void algorithmAvoidsCollision() {
        // Pre-populate with a large set of codes to force retries
        Set<String> existingCodes = new HashSet<>();
        // Add 50 random codes to the "database"
        for (int i = 0; i < 50; i++) {
            existingCodes.add(generateCodeCandidate());
        }

        String newCode = generateUniqueCode(existingCodes);

        assertThat(existingCodes)
                .as("Generated code must not collide with any existing code")
                .doesNotContain(newCode);
    }

    /**
     * Property 9f: Deterministic format — the code format is independent of the
     * number of existing codes (no format degradation under load).
     */
    @Property(tries = 100)
    @Label("P9f: Code format is consistent regardless of existing code count")
    void codeFormatIsConsistentUnderLoad(
            @ForAll @IntRange(min = 0, max = 200) int existingCount) {

        Set<String> existingCodes = new HashSet<>();
        for (int i = 0; i < existingCount; i++) {
            existingCodes.add(generateCodeCandidate());
        }

        String code = generateUniqueCode(existingCodes);

        // Code must be either 8 chars (standard) or 12 chars (fallback)
        assertThat(code.length())
                .as("Code length must be 8 (standard) or 12 (fallback)")
                .isIn(8, 12);

        assertThat(code)
                .as("Code must be uppercase alphanumeric regardless of load")
                .matches("[A-Z0-9]+");
    }

    /**
     * Property 9g: No-collision guarantee — the generated code is never in the
     * existing set, regardless of how many codes already exist.
     */
    @Property(tries = 200)
    @Label("P9g: Generated code is never in the existing code set")
    void generatedCodeNeverInExistingSet(
            @ForAll @IntRange(min = 0, max = 100) int existingCount) {

        Set<String> existingCodes = new HashSet<>();
        for (int i = 0; i < existingCount; i++) {
            existingCodes.add(generateCodeCandidate());
        }

        String code = generateUniqueCode(existingCodes);

        assertThat(existingCodes)
                .as("Generated code must not be present in the existing codes set")
                .doesNotContain(code);
    }
}

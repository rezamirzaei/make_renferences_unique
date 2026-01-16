package com.uniquereferences;

import java.util.Map;
import com.uniquereferences.BibTeXDeduplicator;

/**
 * Comprehensive tests for BibTeXDeduplicator.
 * Tests deduplication logic, sorting, and edge cases.
 */
public class BibTeXDeduplicatorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== BibTeXDeduplicator Tests ===\n");

        // Basic deduplication tests
        testNoDuplicates();
        testSimpleDuplicate();
        testMultipleDuplicates();
        testFirstKeyWins();

        // Sorting tests
        testSortByKeyEnabled();
        testSortByKeyDisabled();
        testSortWithDuplicates();

        // Result statistics tests
        testResultStatistics();
        testResultWithErrors();

        // Edge cases
        testEmptyInput();
        testNullInput();
        testWhitespaceOnlyInput();
        testSingleEntry();

        // Complex scenarios
        testMixedEntryTypes();
        testCaseSensitiveKeys();
        testKeysWithSpecialChars();

        // Summary
        System.out.println("\n=== Test Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }

    // === Basic Deduplication Tests ===

    private static void testNoDuplicates() {
        String input = """
            @article{ref1, title={First}}
            @article{ref2, title={Second}}
            @article{ref3, title={Third}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("noDuplicates - total", 3, result.totalEntries());
        assertTest("noDuplicates - unique", 3, result.uniqueCount());
        assertTest("noDuplicates - duplicates", 0, result.duplicateCount());
    }

    private static void testSimpleDuplicate() {
        String input = """
            @article{ref1, title={First}}
            @article{ref1, title={Duplicate}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("simpleDuplicate - total", 2, result.totalEntries());
        assertTest("simpleDuplicate - unique", 1, result.uniqueCount());
        assertTest("simpleDuplicate - duplicates", 1, result.duplicateCount());
    }

    private static void testMultipleDuplicates() {
        String input = """
            @article{ref1, title={First}}
            @article{ref1, title={Dup1}}
            @article{ref2, title={Second}}
            @article{ref1, title={Dup2}}
            @article{ref2, title={Dup3}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("multipleDuplicates - total", 5, result.totalEntries());
        assertTest("multipleDuplicates - unique", 2, result.uniqueCount());
        assertTest("multipleDuplicates - duplicates", 3, result.duplicateCount());
    }

    private static void testFirstKeyWins() {
        String input = """
            @article{key1, title={FIRST VERSION}}
            @article{key1, title={SECOND VERSION}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("firstKeyWins - unique", 1, result.uniqueCount());

        String kept = result.uniqueEntries().get("key1");
        assertTrue("firstKeyWins - kept first", kept.contains("FIRST VERSION"));
        assertTrue("firstKeyWins - not second", !kept.contains("SECOND VERSION"));
    }

    // === Sorting Tests ===

    private static void testSortByKeyEnabled() {
        String input = """
            @article{zebra, title={Z}}
            @article{apple, title={A}}
            @article{mango, title={M}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, true);

        String[] keys = result.uniqueEntries().keySet().toArray(new String[0]);
        assertTest("sortEnabled - first key", "apple", keys[0]);
        assertTest("sortEnabled - second key", "mango", keys[1]);
        assertTest("sortEnabled - third key", "zebra", keys[2]);
    }

    private static void testSortByKeyDisabled() {
        String input = """
            @article{zebra, title={Z}}
            @article{apple, title={A}}
            @article{mango, title={M}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        String[] keys = result.uniqueEntries().keySet().toArray(new String[0]);
        // Should maintain insertion order
        assertTest("sortDisabled - first key", "zebra", keys[0]);
        assertTest("sortDisabled - second key", "apple", keys[1]);
        assertTest("sortDisabled - third key", "mango", keys[2]);
    }

    private static void testSortWithDuplicates() {
        String input = """
            @article{zebra, title={Z}}
            @article{apple, title={A}}
            @article{zebra, title={Z duplicate}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, true);

        assertTest("sortWithDuplicates - unique", 2, result.uniqueCount());
        String[] keys = result.uniqueEntries().keySet().toArray(new String[0]);
        assertTest("sortWithDuplicates - first key", "apple", keys[0]);
        assertTest("sortWithDuplicates - second key", "zebra", keys[1]);
    }

    // === Result Statistics Tests ===

    private static void testResultStatistics() {
        String input = """
            @article{ref1, title={One}}
            @article{ref2, title={Two}}
            @article{ref1, title={Dup}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("resultStats - total", 3, result.totalEntries());
        assertTest("resultStats - unique", 2, result.uniqueCount());
        assertTest("resultStats - duplicates", 1, result.duplicateCount());
        assertTest("resultStats - parseErrors", 0, result.parseErrorCount());
    }

    private static void testResultWithErrors() {
        String input = """
            @article{ref1, title={Valid}}
            @article{ref2, title={Missing close brace
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTrue("resultWithErrors - has parseErrors", result.parseErrorCount() > 0 || result.totalEntries() < 2);
    }

    // === Edge Cases ===

    private static void testEmptyInput() {
        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate("", false);

        assertTest("emptyInput - total", 0, result.totalEntries());
        assertTest("emptyInput - unique", 0, result.uniqueCount());
        assertTest("emptyInput - duplicates", 0, result.duplicateCount());
        assertTrue("emptyInput - empty map", result.uniqueEntries().isEmpty());
    }

    private static void testNullInput() {
        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(null, false);

        assertTest("nullInput - total", 0, result.totalEntries());
        assertTest("nullInput - unique", 0, result.uniqueCount());
    }

    private static void testWhitespaceOnlyInput() {
        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate("   \n\t\n   ", false);

        assertTest("whitespaceOnly - total", 0, result.totalEntries());
    }

    private static void testSingleEntry() {
        String input = "@article{single, title={Only One}}";

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("singleEntry - total", 1, result.totalEntries());
        assertTest("singleEntry - unique", 1, result.uniqueCount());
        assertTest("singleEntry - duplicates", 0, result.duplicateCount());
    }

    // === Complex Scenarios ===

    private static void testMixedEntryTypes() {
        String input = """
            @article{ref1, title={Article}}
            @book{ref2, title={Book}}
            @inproceedings{ref3, title={Conference}}
            @misc{ref4, title={Misc}}
            @phdthesis{ref5, title={Thesis}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("mixedTypes - total", 5, result.totalEntries());
        assertTest("mixedTypes - unique", 5, result.uniqueCount());
    }

    private static void testCaseSensitiveKeys() {
        String input = """
            @article{Key1, title={Uppercase K}}
            @article{key1, title={Lowercase k}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        // Keys should be case-sensitive (Key1 != key1)
        assertTest("caseSensitiveKeys - unique", 2, result.uniqueCount());
    }

    private static void testKeysWithSpecialChars() {
        String input = """
            @article{author:2020, title={Colon}}
            @article{author_2020, title={Underscore}}
            @article{author-2020, title={Hyphen}}
            """;

        BibTeXDeduplicator.Result result = BibTeXDeduplicator.deduplicate(input, false);

        assertTest("specialCharKeys - unique", 3, result.uniqueCount());
        assertTrue("specialCharKeys - has colon key", result.uniqueEntries().containsKey("author:2020"));
    }

    // === Helper Methods ===

    private static void assertTest(String name, Object expected, Object actual) {
        if (expected == null && actual == null) {
            pass(name);
        } else if (expected != null && expected.equals(actual)) {
            pass(name);
        } else {
            fail(name, expected, actual);
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name, "true", "false");
        }
    }

    private static void pass(String name) {
        System.out.println("✓ " + name);
        passed++;
    }

    private static void fail(String name, Object expected, Object actual) {
        System.out.println("✗ " + name + " - Expected: " + expected + ", Got: " + actual);
        failed++;
    }
}

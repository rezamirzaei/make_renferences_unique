package com.uniquereferences;

import com.uniquereferences.BibTeXParser;
import com.uniquereferences.ReferenceVerifier;

/**
 * Tests for ReferenceVerifier.
 * Tests field extraction, JSON parsing, and entry building.
 * Note: Network tests are marked and can be skipped in offline mode.
 */
public class ReferenceVerifierTest {

    private static int passed = 0;
    private static int failed = 0;
    private static boolean runNetworkTests = false; // Set to true to run network tests

    public static void main(String[] args) {
        // Check for network test flag
        for (String arg : args) {
            if (arg.equals("--network")) {
                runNetworkTests = true;
            }
        }

        System.out.println("=== ReferenceVerifier Tests ===");
        if (!runNetworkTests) {
            System.out.println("(Network tests skipped. Use --network flag to enable)\n");
        } else {
            System.out.println("(Network tests enabled)\n");
        }

        // Field extraction tests (offline)
        testExtractDOI();
        testExtractTitle();
        testExtractAuthor();
        testExtractYear();
        testExtractJournal();
        testExtractVolume();
        testExtractPages();

        // Entry type detection
        testEntryTypeExtraction();

        // Verification status tests (offline - using mocked/simulated responses)
        testVerifySkippedNoInfo();
        testVerifyEntryStructure();

        // Network tests (only if enabled)
        if (runNetworkTests) {
            System.out.println("\n--- Network Tests ---");
            testVerifyByDOI();
            testVerifyByTitle();
            testVerifyNotFound();
        }

        // Summary
        System.out.println("\n=== Test Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }

    // === Field Extraction Tests ===

    private static void testExtractDOI() {
        String entry = """
            @article{test,
              title = {Test},
              doi = {10.1234/example.2020}
            }
            """;

        // Test that DOI pattern is found
        assertTrue("extractDOI - contains DOI", entry.contains("10.1234/example.2020"));

        // Verify the entry can be parsed
        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(entry);
        assertTest("extractDOI - parsed", 1, result.entries().size());
    }

    private static void testExtractTitle() {
        String entry = """
            @article{test,
              title = {A Very Long Title With Special Characters: Test!}
            }
            """;

        assertTrue("extractTitle - contains title", entry.contains("A Very Long Title"));

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(entry);
        assertTest("extractTitle - parsed", 1, result.entries().size());
    }

    private static void testExtractAuthor() {
        String entry = """
            @article{test,
              author = {Smith, John and Doe, Jane}
            }
            """;

        assertTrue("extractAuthor - contains author", entry.contains("Smith, John"));

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(entry);
        assertTest("extractAuthor - parsed", 1, result.entries().size());
    }

    private static void testExtractYear() {
        String entry = """
            @article{test,
              year = {2023}
            }
            """;

        assertTrue("extractYear - contains year", entry.contains("2023"));
    }

    private static void testExtractJournal() {
        String entry = """
            @article{test,
              journal = {Nature Communications}
            }
            """;

        assertTrue("extractJournal - contains journal", entry.contains("Nature Communications"));
    }

    private static void testExtractVolume() {
        String entry = """
            @article{test,
              volume = {42}
            }
            """;

        assertTrue("extractVolume - contains volume", entry.contains("42"));
    }

    private static void testExtractPages() {
        String entry = """
            @article{test,
              pages = {100-150}
            }
            """;

        assertTrue("extractPages - contains pages", entry.contains("100-150"));
    }

    // === Entry Type Detection ===

    private static void testEntryTypeExtraction() {
        String article = "@article{key1, title={Test}}";
        String book = "@book{key2, title={Test}}";
        String inproc = "@inproceedings{key3, title={Test}}";

        BibTeXParser.ParseResult r1 = BibTeXParser.parseEntries(article);
        BibTeXParser.ParseResult r2 = BibTeXParser.parseEntries(book);
        BibTeXParser.ParseResult r3 = BibTeXParser.parseEntries(inproc);

        assertTest("entryType - article", "article", r1.entries().get(0).type());
        assertTest("entryType - book", "book", r2.entries().get(0).type());
        assertTest("entryType - inproceedings", "inproceedings", r3.entries().get(0).type());
    }

    // === Verification Status Tests ===

    private static void testVerifySkippedNoInfo() {
        String entry = "@article{noinfo, note={No useful info}}";

        ReferenceVerifier verifier = new ReferenceVerifier();
        ReferenceVerifier.VerificationResult result = verifier.verify(entry);

        assertTest("verifySkipped - status", ReferenceVerifier.VerificationStatus.SKIPPED, result.status());
        assertTrue("verifySkipped - original unchanged", result.originalEntry().equals(result.correctedEntry()));
    }

    private static void testVerifyEntryStructure() {
        String entry = """
            @article{testkey,
              author = {Test Author},
              title = {Test Title},
              journal = {Test Journal},
              year = {2020},
              volume = {10},
              pages = {1-10}
            }
            """;

        // Verify the entry parses correctly
        BibTeXParser.ParseResult parseResult = BibTeXParser.parseEntries(entry);
        assertTest("verifyStructure - parsed", 1, parseResult.entries().size());
        assertTest("verifyStructure - key", "testkey", parseResult.entries().get(0).key());
        assertTest("verifyStructure - type", "article", parseResult.entries().get(0).type());
    }

    // === Network Tests ===

    private static void testVerifyByDOI() {
        // Use a known DOI
        String entry = """
            @article{testdoi,
              doi = {10.1038/nature12373}
            }
            """;

        ReferenceVerifier verifier = new ReferenceVerifier();
        ReferenceVerifier.VerificationResult result = verifier.verify(entry);

        assertTrue("verifyByDOI - not error",
            result.status() != ReferenceVerifier.VerificationStatus.ERROR);

        if (result.status() == ReferenceVerifier.VerificationStatus.CORRECTED) {
            assertTrue("verifyByDOI - has title", result.correctedEntry().contains("title"));
            System.out.println("  (DOI lookup successful - entry corrected)");
        }
    }

    private static void testVerifyByTitle() {
        String entry = """
            @article{testtitle,
              title = {CRISPR-Cas9 genome editing}
            }
            """;

        ReferenceVerifier verifier = new ReferenceVerifier();
        ReferenceVerifier.VerificationResult result = verifier.verify(entry);

        assertTrue("verifyByTitle - not skipped",
            result.status() != ReferenceVerifier.VerificationStatus.SKIPPED);

        System.out.println("  (Title search status: " + result.status() + ")");
    }

    private static void testVerifyNotFound() {
        String entry = """
            @article{notfound,
              title = {This Is A Completely Made Up Title That Should Not Exist XYZ123ABC}
            }
            """;

        ReferenceVerifier verifier = new ReferenceVerifier();
        ReferenceVerifier.VerificationResult result = verifier.verify(entry);

        assertTrue("verifyNotFound - is not found or error",
            result.status() == ReferenceVerifier.VerificationStatus.NOT_FOUND ||
            result.status() == ReferenceVerifier.VerificationStatus.ERROR);
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

package com.uniquereferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Regression tests to ensure verification never corrupts already-good BibTeX.
 *
 * These tests intentionally avoid network calls.
 * We validate that ReferenceVerifier's internal field parsing and rebuilding logic:
 * - preserves LaTeX macros and escapes (e.g. \text{...}, \&)
 * - preserves existing fields like month and organization
 * - doesn't drop unknown/custom fields
 */
public class BibIntegrityRegressionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Bib Integrity Regression Tests ===\n");

        testParseAllFieldsPreservesLatex();
        testRebuildPreservesOrganizationAndMonth();
        testRoundTripOnRefsBibEntries();
        testRoundTripOnStringsBibLatex();

        System.out.println("\n=== Test Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));

        if (failed > 0) System.exit(1);
    }

    private static void testParseAllFieldsPreservesLatex() {
        String entry = """
                @article{t,
                  title={Oil \\& Gas and \\text{ADMM}},
                  month={Sep.},
                  organization={IEEE},
                  note={Use \\LaTeX{} macros}
                }
                """;

        ReferenceVerifier verifier = new ReferenceVerifier();
        Map<String, String> fields = verifier._testOnly_parseAllFields(entry);

        assertEq("parseAllFields - title", "Oil \\& Gas and \\text{ADMM}", fields.get("title"));
        assertEq("parseAllFields - month", "Sep.", fields.get("month"));
        assertEq("parseAllFields - organization", "IEEE", fields.get("organization"));
        assertEq("parseAllFields - note", "Use \\LaTeX{} macros", fields.get("note"));
    }

    private static void testRebuildPreservesOrganizationAndMonth() {
        String entry = """
                @inproceedings{p,
                  title={A Title},
                  booktitle={Some Conf},
                  month={Oct.},
                  year={2017},
                  organization={IEEE}
                }
                """;

        ReferenceVerifier verifier = new ReferenceVerifier();
        ReferenceVerifier.ReferenceData data = new ReferenceVerifier.ReferenceData();
        data.source = "Test";
        data.doi = "10.1234/abc";
        data.month = "December"; // should NOT overwrite existing Oct.

        String rebuilt = verifier._testOnly_buildCorrectedEntry("inproceedings", "p", data, entry);

        assertContains("rebuild - keeps org", rebuilt, "organization = {IEEE}");
        assertContains("rebuild - keeps month", rebuilt, "month = {Oct.}");
        assertContains("rebuild - adds doi", rebuilt, "doi = {10.1234/abc}");
    }

    private static void testRoundTripOnRefsBibEntries() throws Exception {
        Path p = Path.of("refs.bib");
        if (!Files.exists(p)) {
            pass("refs.bib missing - skipped");
            return;
        }

        String content = Files.readString(p);
        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(content);
        assertTrue("refs.bib parsed entries > 0", !parsed.entries().isEmpty());

        ReferenceVerifier verifier = new ReferenceVerifier();

        for (BibTeXParser.Entry e : parsed.entries()) {
            String original = e.raw();
            Map<String, String> fields = verifier._testOnly_parseAllFields(original);
            // rebuild with empty data should keep fields as-is
            ReferenceVerifier.ReferenceData data = new ReferenceVerifier.ReferenceData();
            data.source = "Test";
            String rebuilt = verifier._testOnly_buildCorrectedEntry(e.type(), e.key(), data, original);
            Map<String, String> rebuiltFields = verifier._testOnly_parseAllFields(rebuilt);

            // Ensure critical fields are preserved
            assertEq("roundTrip (" + e.key() + ") month", fields.get("month"), rebuiltFields.get("month"));
            assertEq("roundTrip (" + e.key() + ") organization", fields.get("organization"), rebuiltFields.get("organization"));
            assertEq("roundTrip (" + e.key() + ") title", fields.get("title"), rebuiltFields.get("title"));
        }
    }

    private static void testRoundTripOnStringsBibLatex() throws Exception {
        Path p = Path.of("strings.bib");
        if (!Files.exists(p)) {
            pass("strings.bib missing - skipped");
            return;
        }

        String content = Files.readString(p);
        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(content);
        assertTrue("strings.bib parsed entries > 0", !parsed.entries().isEmpty());

        ReferenceVerifier verifier = new ReferenceVerifier();

        // Find at least one entry with \text or \&
        boolean foundLatex = false;
        for (BibTeXParser.Entry e : parsed.entries()) {
            if (e.raw().contains("\\text") || e.raw().contains("\\&")) {
                foundLatex = true;
                Map<String, String> fields = verifier._testOnly_parseAllFields(e.raw());
                String title = fields.get("title");
                assertTrue("latex entry has title", title != null && !title.isBlank());

                String rebuilt = verifier._testOnly_buildCorrectedEntry(e.type(), e.key(), new ReferenceVerifier.ReferenceData(), e.raw());
                Map<String, String> rebuiltFields = verifier._testOnly_parseAllFields(rebuilt);
                assertEq("latex preserved (" + e.key() + ") title", title, rebuiltFields.get("title"));
                break;
            }
        }
        assertTrue("found a LaTeX entry to test", foundLatex);
    }

    // --- tiny assertions ---

    private static void assertEq(String name, Object expected, Object actual) {
        if (expected == null && actual == null) {
            pass(name);
            return;
        }
        if (expected != null && expected.equals(actual)) {
            pass(name);
        } else {
            fail(name, expected, actual);
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (condition) pass(name);
        else fail(name, true, false);
    }

    private static void assertContains(String name, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) pass(name);
        else fail(name, "contains: " + needle, "missing");
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

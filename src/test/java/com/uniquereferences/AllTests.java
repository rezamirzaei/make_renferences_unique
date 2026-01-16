package com.uniquereferences;

/**
 * Main test runner that executes all test suites.
 *
 * Usage:
 *   java AllTests           - Run all tests (skip network tests)
 *   java AllTests --network - Run all tests including network tests
 */
public class AllTests {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        Unique LaTeX References - Test Suite              ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        boolean allPassed = true;

        // Run BibTeXParser tests
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│ Running BibTeXParserTest...                              │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        try {
            BibTeXParserTest.main(new String[]{});
        } catch (Exception e) {
            System.out.println("BibTeXParserTest FAILED: " + e.getMessage());
            allPassed = false;
        }

        System.out.println();

        // Run BibTeXDeduplicator tests
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│ Running BibTeXDeduplicatorTest...                        │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        try {
            BibTeXDeduplicatorTest.main(new String[]{});
        } catch (Exception e) {
            System.out.println("BibTeXDeduplicatorTest FAILED: " + e.getMessage());
            allPassed = false;
        }

        System.out.println();

        // Run ReferenceVerifier tests
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│ Running ReferenceVerifierTest...                         │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        try {
            ReferenceVerifierTest.main(args); // Pass args for --network flag
        } catch (Exception e) {
            System.out.println("ReferenceVerifierTest FAILED: " + e.getMessage());
            allPassed = false;
        }

        System.out.println();

        // Run Bib integrity regression tests
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│ Running BibIntegrityRegressionTest...                    │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        try {
            BibIntegrityRegressionTest.main(new String[]{});
        } catch (Exception e) {
            System.out.println("BibIntegrityRegressionTest FAILED: " + e.getMessage());
            allPassed = false;
        }

        // Final summary
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        if (allPassed) {
            System.out.println("║              ✓ ALL TEST SUITES PASSED                    ║");
        } else {
            System.out.println("║              ✗ SOME TESTS FAILED                         ║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        if (!allPassed) {
            System.exit(1);
        }
    }
}

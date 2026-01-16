package com.uniquereferences;

import com.uniquereferences.BibTeXParser;

/**
 * Comprehensive tests for BibTeXParser.
 * Tests parsing of various BibTeX entry formats, edge cases, and error handling.
 */
public class BibTeXParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== BibTeXParser Tests ===\n");

        // Basic parsing tests
        testParseSimpleArticle();
        testParseBook();
        testParseInProceedings();
        testParseMultipleEntries();

        // Key extraction tests
        testKeyWithSpaces();
        testKeyWithNumbers();
        testKeyWithSpecialChars();

        // Brace handling tests
        testNestedBraces();
        testBracesInTitle();
        testUnbalancedBraces();

        // Quote handling tests
        testQuotedStrings();
        testEscapedQuotes();

        // Entry type tests
        testSkipCommentEntry();
        testSkipPreambleEntry();
        testSkipStringEntry();

        // Edge cases
        testEmptyInput();
        testNullInput();
        testNoEntries();
        testEntryWithoutKey();
        testParenthesesDelimiters();
        testMixedDelimiters();

        // Error handling
        testUnclosedEntry();
        testMalformedEntry();

        // Summary
        System.out.println("\n=== Test Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }

    // === Basic Parsing Tests ===

    private static void testParseSimpleArticle() {
        String input = """
            @article{smith2020,
              author = {John Smith},
              title = {A Great Paper},
              journal = {Nature},
              year = {2020}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("parseSimpleArticle - entry count", 1, result.entries().size());
        assertTest("parseSimpleArticle - key", "smith2020", result.entries().get(0).key());
        assertTest("parseSimpleArticle - type", "article", result.entries().get(0).type());
        assertTest("parseSimpleArticle - no errors", 0, result.errors().size());
    }

    private static void testParseBook() {
        String input = """
            @book{knuth1997,
              author = {Donald Knuth},
              title = {The Art of Computer Programming},
              publisher = {Addison-Wesley},
              year = {1997}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("parseBook - entry count", 1, result.entries().size());
        assertTest("parseBook - key", "knuth1997", result.entries().get(0).key());
        assertTest("parseBook - type", "book", result.entries().get(0).type());
    }

    private static void testParseInProceedings() {
        String input = """
            @inproceedings{conf2021,
              author = {Jane Doe},
              title = {Conference Paper},
              booktitle = {Proceedings of ABC},
              year = {2021}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("parseInProceedings - entry count", 1, result.entries().size());
        assertTest("parseInProceedings - type", "inproceedings", result.entries().get(0).type());
    }

    private static void testParseMultipleEntries() {
        String input = """
            @article{ref1, title={First}}
            @book{ref2, title={Second}}
            @misc{ref3, title={Third}}
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("parseMultipleEntries - entry count", 3, result.entries().size());
        assertTest("parseMultipleEntries - first key", "ref1", result.entries().get(0).key());
        assertTest("parseMultipleEntries - second key", "ref2", result.entries().get(1).key());
        assertTest("parseMultipleEntries - third key", "ref3", result.entries().get(2).key());
    }

    // === Key Extraction Tests ===

    private static void testKeyWithSpaces() {
        String input = "@article{  spacedKey  , title={Test}}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("keyWithSpaces - entry count", 1, result.entries().size());
        assertTest("keyWithSpaces - key trimmed", "spacedKey", result.entries().get(0).key());
    }

    private static void testKeyWithNumbers() {
        String input = "@article{author2020paper123, title={Test}}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("keyWithNumbers - key", "author2020paper123", result.entries().get(0).key());
    }

    private static void testKeyWithSpecialChars() {
        String input = "@article{author_2020-paper:v1, title={Test}}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("keyWithSpecialChars - entry count", 1, result.entries().size());
        assertTest("keyWithSpecialChars - key", "author_2020-paper:v1", result.entries().get(0).key());
    }

    // === Brace Handling Tests ===

    private static void testNestedBraces() {
        String input = """
            @article{nested,
              title = {A {Nested} Title with {Multiple {Levels}}}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("nestedBraces - entry count", 1, result.entries().size());
        assertTest("nestedBraces - no errors", 0, result.errors().size());
        assertTrue("nestedBraces - contains nested", result.entries().get(0).raw().contains("{Nested}"));
    }

    private static void testBracesInTitle() {
        String input = """
            @article{mathpaper,
              title = {Analysis of $O(n^2)$ Algorithms}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("bracesInTitle - entry count", 1, result.entries().size());
    }

    private static void testUnbalancedBraces() {
        String input = "@article{unbalanced, title={Missing close";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTrue("unbalancedBraces - has errors", result.errors().size() > 0);
    }

    // === Quote Handling Tests ===

    private static void testQuotedStrings() {
        String input = """
            @article{quoted,
              title = "A Title in Quotes",
              author = "John Doe"
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("quotedStrings - entry count", 1, result.entries().size());
        assertTest("quotedStrings - key", "quoted", result.entries().get(0).key());
    }

    private static void testEscapedQuotes() {
        String input = """
            @article{escaped,
              title = {He said \\"Hello\\"}
            }
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("escapedQuotes - entry count", 1, result.entries().size());
    }

    // === Skip Non-Reference Entries Tests ===

    private static void testSkipCommentEntry() {
        String input = """
            @comment{This is a comment}
            @article{real, title={Real Entry}}
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("skipComment - entry count", 1, result.entries().size());
        assertTest("skipComment - key", "real", result.entries().get(0).key());
    }

    private static void testSkipPreambleEntry() {
        String input = """
            @preamble{"Some preamble text"}
            @article{ref1, title={Test}}
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("skipPreamble - entry count", 1, result.entries().size());
    }

    private static void testSkipStringEntry() {
        String input = """
            @string{myjournal = "Journal of Testing"}
            @article{ref1, journal = myjournal, title={Test}}
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("skipString - entry count", 1, result.entries().size());
    }

    // === Edge Cases ===

    private static void testEmptyInput() {
        BibTeXParser.ParseResult result = BibTeXParser.parseEntries("");

        assertTest("emptyInput - entry count", 0, result.entries().size());
        assertTest("emptyInput - no errors", 0, result.errors().size());
    }

    private static void testNullInput() {
        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(null);

        assertTest("nullInput - entry count", 0, result.entries().size());
        assertTest("nullInput - no errors", 0, result.errors().size());
    }

    private static void testNoEntries() {
        String input = "This is just plain text with no BibTeX entries.";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("noEntries - entry count", 0, result.entries().size());
    }

    private static void testEntryWithoutKey() {
        String input = "@article{, title={No Key}}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        // Entry without key should be reported as an error
        assertTrue("entryWithoutKey - has error or no entry",
            result.entries().size() == 0 || result.errors().size() > 0);
    }

    private static void testParenthesesDelimiters() {
        String input = "@article(parenKey, title={Parentheses})";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("parenDelimiters - entry count", 1, result.entries().size());
        assertTest("parenDelimiters - key", "parenKey", result.entries().get(0).key());
    }

    private static void testMixedDelimiters() {
        String input = """
            @article{braceKey, title={Braces}}
            @book(parenKey, title={Parentheses})
            """;

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTest("mixedDelimiters - entry count", 2, result.entries().size());
    }

    // === Error Handling ===

    private static void testUnclosedEntry() {
        String input = "@article{unclosed, title={Test}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        assertTrue("unclosedEntry - has errors", result.errors().size() > 0);
    }

    private static void testMalformedEntry() {
        String input = "@{noType, title={Test}}";

        BibTeXParser.ParseResult result = BibTeXParser.parseEntries(input);

        // Malformed entry should be skipped
        assertTest("malformedEntry - entry count", 0, result.entries().size());
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

/**
 * Tiny self-test runner (no external dependencies) to verify BibTeX parsing/dedup behavior.
 *
 * <p>Run: java BibTeXParserSelfTest
 */
public class BibTeXParserSelfTest {

    public static void main(String[] args) {
        testBasicDedup();
        testNestedBracesAndQuotes();
        testSkipStringAndComment();
        testUnclosedEntry();
        System.out.println("All tests passed.");
    }

    private static void testBasicDedup() {
        String input = """
            @article{key1,
              title={A}
            }
            @article{key1,
              title={B}
            }
            @book{key2,
              title={C}
            }
            """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        assertEquals(2, r.uniqueCount(), "uniqueCount");
        assertEquals(3, r.totalEntries(), "totalEntries");
        assertEquals(1, r.duplicateCount(), "duplicateCount");
        assertTrue(r.uniqueEntries().containsKey("key1"), "contains key1");
        assertTrue(r.uniqueEntries().get("key1").contains("title={A}"), "first wins");
    }

    private static void testNestedBracesAndQuotes() {
        String input = """
            @article{key3,
              title={A {B} C},
              note="Brace in quotes } should not end",
              howpublished={\\url{https://example.com?a={b}}}
            }
            """;

        var parsed = BibTeXParser.parseEntries(input);
        assertEquals(1, parsed.entries().size(), "entries size");
        assertEquals("key3", parsed.entries().get(0).key(), "key");
    }

    private static void testSkipStringAndComment() {
        String input = """
            @string{ foo = "bar" }
            @comment{ this is ignored }
            @preamble{ "xx" }
            @article{key4, title={Ok}}
            """;

        var parsed = BibTeXParser.parseEntries(input);
        assertEquals(1, parsed.entries().size(), "entries size");
        assertEquals("key4", parsed.entries().get(0).key(), "key");
    }

    private static void testUnclosedEntry() {
        String input = "@article{key5, title={X}"; // missing closing brace
        var parsed = BibTeXParser.parseEntries(input);
        assertEquals(0, parsed.entries().size(), "entries size");
        assertTrue(!parsed.errors().isEmpty(), "errors present");
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError("Assertion failed for " + name + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Assertion failed for " + name);
        }
    }
}

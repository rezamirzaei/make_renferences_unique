package com.uniquereferences;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceVerifierParsingJUnitTest {

    @Test
    void parseAllFields_handlesNestedBracesAndCommas() {
        String entry = """
                @article{a,
                  title={A title with {nested {braces}} and, comma},
                  note={A note with {b1, b2} and {more {nesting}}},
                  month={Sep.}
                }
                """;

        ReferenceVerifier v = new ReferenceVerifier();
        Map<String, String> fields = v._testOnly_parseAllFields(entry);

        assertEquals("A title with {nested {braces}} and, comma", fields.get("title"));
        assertEquals("A note with {b1, b2} and {more {nesting}}", fields.get("note"));
        assertEquals("Sep.", fields.get("month"));
    }

    @Test
    void rebuild_doesNotCorruptRefsBibTitles() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of("refs.bib");
        if (!java.nio.file.Files.exists(p)) {
            return;
        }

        String content = java.nio.file.Files.readString(p);
        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(content);
        assertFalse(parsed.entries().isEmpty());

        ReferenceVerifier v = new ReferenceVerifier();
        for (BibTeXParser.Entry e : parsed.entries()) {
            String original = e.raw();
            String rebuilt = v._testOnly_buildCorrectedEntry(e.type(), e.key(), new ReferenceVerifier.ReferenceData(), original);

            // Ensure the Oil \& sequence and "{...}" structure stays intact where present
            if (original.contains("\\&")) {
                assertTrue(rebuilt.contains("\\&"), "Rebuilt entry should preserve \\\\&");
            }

            // Round-trip parse still contains title
            Map<String, String> rebuiltFields = v._testOnly_parseAllFields(rebuilt);
            assertEquals(v._testOnly_parseAllFields(original).get("title"), rebuiltFields.get("title"));
        }
    }
}

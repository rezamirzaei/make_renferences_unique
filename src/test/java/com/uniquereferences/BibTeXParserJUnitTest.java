package com.uniquereferences;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BibTeXParserJUnitTest {

    @Test
    void parseEntries_emptyAndNull() {
        assertEquals(0, BibTeXParser.parseEntries("").entries().size());
        assertEquals(0, BibTeXParser.parseEntries(null).entries().size());
    }

    @Test
    void parseEntries_parenDelimiters() {
        String input = "@article(key, title={T})";
        var r = BibTeXParser.parseEntries(input);
        assertEquals(1, r.entries().size());
        assertEquals("key", r.entries().get(0).key());
    }

    @Test
    void extractField_handlesWhitespaceAndNewlines() {
        String raw = "@article{a,\n  title={Hello World},\n  year={2020}\n}";
        assertEquals("Hello World", BibTeXParser.extractField(raw, "title"));
        assertEquals("2020", BibTeXParser.extractField(raw, "year"));
    }
}

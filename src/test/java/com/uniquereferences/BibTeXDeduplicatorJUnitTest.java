package com.uniquereferences;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BibTeXDeduplicatorJUnitTest {

    @Test
    void keyDedup_firstWins() {
        String input = """
                @article{key1, title={FIRST VERSION}}
                @article{key1, title={SECOND VERSION}}
                """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        assertEquals(2, r.totalEntries());
        assertEquals(1, r.uniqueCount());
        assertEquals(1, r.duplicateCount());
        assertTrue(r.uniqueEntries().get("key1").contains("FIRST VERSION"));
    }

    @Test
    void smartDedup_sameTitleDifferentKeys() {
        String bib = """
                @article{a,
                  title={Hello World},
                  year={2020}
                }

                @article{b,
                  title={Hello   World!},
                  year={2020}
                }
                """;

        var r = BibTeXDeduplicator.deduplicate(bib, true, true);
        assertEquals(2, r.totalEntries());
        assertEquals(1, r.uniqueCount());
        assertEquals(1, r.duplicateCount());
        assertEquals(1, r.duplicates().size());
        assertEquals("b", r.duplicates().get(0).droppedKey());
        assertEquals("a", r.duplicates().get(0).keptKey());
        assertEquals(BibTeXDeduplicator.DuplicateReason.TITLE_DUPLICATE, r.duplicates().get(0).reason());
    }

    @Test
    void normalizeMonths_convertsToFullName() {
        String input = """
                @article{key1,
                  title={Test Article},
                  month={Sep.},
                  year={2020}
                }
                """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        Map<String, String> normalized = BibTeXDeduplicator.normalizeMonths(r.uniqueEntries(), MonthNormalizer.MonthStyle.FULL_NAME);

        String entry = normalized.get("key1");
        assertTrue(entry.contains("September"), "Month should be normalized to September, got: " + entry);
        assertFalse(entry.contains("Sep."), "Original month abbreviation should be replaced");
    }

    @Test
    void normalizeMonths_convertsToAbbrevDot() {
        String input = """
                @article{key1,
                  title={Test Article},
                  month={December},
                  year={2020}
                }
                """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        Map<String, String> normalized = BibTeXDeduplicator.normalizeMonths(r.uniqueEntries(), MonthNormalizer.MonthStyle.ABBREV_DOT);

        String entry = normalized.get("key1");
        assertTrue(entry.contains("Dec."), "Month should be normalized to Dec., got: " + entry);
        assertFalse(entry.contains("December"), "Original month should be replaced");
    }

    @Test
    void normalizeMonths_keepOriginalDoesNotChange() {
        String input = """
                @article{key1,
                  title={Test Article},
                  month={Sep.},
                  year={2020}
                }
                """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        Map<String, String> normalized = BibTeXDeduplicator.normalizeMonths(r.uniqueEntries(), MonthNormalizer.MonthStyle.KEEP_ORIGINAL);

        String entry = normalized.get("key1");
        assertTrue(entry.contains("Sep."), "Month should remain Sep.");
    }

    @Test
    void normalizeMonths_handlesMultipleEntries() {
        String input = """
                @article{a, title={A}, month={January}, year={2020}}
                @article{b, title={B}, month={Feb.}, year={2021}}
                @article{c, title={C}, month={March}, year={2022}}
                """;

        var r = BibTeXDeduplicator.deduplicate(input, false);
        Map<String, String> normalized = BibTeXDeduplicator.normalizeMonths(r.uniqueEntries(), MonthNormalizer.MonthStyle.ABBREV_DOT);

        assertTrue(normalized.get("a").contains("Jan."));
        assertTrue(normalized.get("b").contains("Feb."));
        assertTrue(normalized.get("c").contains("Mar."));
    }
}

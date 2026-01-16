package com.uniquereferences;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceVerifierJUnitTest {

    @Test
    void extractField_smoke() {
        String entry = """
                @article{test,
                  title = {A Very Long Title With Special Characters: Test!},
                  author = {Smith, John and Doe, Jane},
                  year = {2023},
                  journal = {Nature Communications},
                  volume = {42},
                  pages = {100-150},
                  doi = {10.1234/example.2020}
                }
                """;

        assertEquals("10.1234/example.2020", BibTeXParser.extractField(entry, "doi"));
        assertTrue(BibTeXParser.extractField(entry, "title").contains("A Very Long Title"));
        assertTrue(BibTeXParser.extractField(entry, "author").contains("Smith, John"));
        assertEquals("2023", BibTeXParser.extractField(entry, "year"));
        assertEquals("Nature Communications", BibTeXParser.extractField(entry, "journal"));
        assertEquals("42", BibTeXParser.extractField(entry, "volume"));
        assertEquals("100-150", BibTeXParser.extractField(entry, "pages"));
    }

    @Test
    void safeMode_doesNotOverwriteMonth() {
        String entry = """
                @article{x,
                  title={T},
                  year={2020},
                  month={Sep.}
                }
                """;

        ReferenceVerifier.ReferenceData data = new ReferenceVerifier.ReferenceData();
        data.source = "Test";
        data.month = "December";

        ReferenceVerifier verifier = new ReferenceVerifier(ReferenceVerifier.VerificationMode.SAFE, MonthNormalizer.MonthStyle.FULL_NAME);
        String rebuilt = verifier._testOnly_buildCorrectedEntry("article", "x", data, entry);

        assertTrue(rebuilt.contains("month = {Sep.}"));
    }

    @Test
    void aggressiveMode_overwritesMonthUsingSelectedStyle() {
        String entry = """
                @article{x,
                  title={T},
                  year={2020},
                  month={Sep.}
                }
                """;

        ReferenceVerifier.ReferenceData data = new ReferenceVerifier.ReferenceData();
        data.source = "Test";
        data.month = "December";

        ReferenceVerifier verifier = new ReferenceVerifier(ReferenceVerifier.VerificationMode.AGGRESSIVE_DOI_ONLY, MonthNormalizer.MonthStyle.ABBREV_DOT);
        String rebuilt = verifier._testOnly_buildCorrectedEntryAggressive("article", "x", data, entry);

        assertTrue(rebuilt.contains("month = {Dec.}"));
    }
}

package com.uniquereferences;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BibIntegrityRegressionJUnitTest {

    @Test
    void parseAllFields_preservesLatexAndCustomFields() {
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

        assertEquals("Oil \\& Gas and \\text{ADMM}", fields.get("title"));
        assertEquals("Sep.", fields.get("month"));
        assertEquals("IEEE", fields.get("organization"));
        assertEquals("Use \\LaTeX{} macros", fields.get("note"));
    }

    @Test
    void rebuild_preservesOrganizationAndMonth_addsDoi() {
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
        data.month = "December"; // should NOT overwrite existing Oct. in SAFE mode

        String rebuilt = verifier._testOnly_buildCorrectedEntry("inproceedings", "p", data, entry);

        assertTrue(rebuilt.contains("organization = {IEEE}"));
        assertTrue(rebuilt.contains("month = {Oct.}"));
        assertTrue(rebuilt.contains("doi = {10.1234/abc}"));
    }

    @Test
    void roundTrip_refsBib_preservesKeyFieldsIfFileExists() throws Exception {
        Path p = Path.of("refs.bib");
        if (!Files.exists(p)) return;

        String content = Files.readString(p);
        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(content);
        assertFalse(parsed.entries().isEmpty());

        ReferenceVerifier verifier = new ReferenceVerifier();

        for (BibTeXParser.Entry e : parsed.entries()) {
            String original = e.raw();
            Map<String, String> fields = verifier._testOnly_parseAllFields(original);

            ReferenceVerifier.ReferenceData data = new ReferenceVerifier.ReferenceData();
            data.source = "Test";

            String rebuilt = verifier._testOnly_buildCorrectedEntry(e.type(), e.key(), data, original);
            Map<String, String> rebuiltFields = verifier._testOnly_parseAllFields(rebuilt);

            assertEquals(fields.get("month"), rebuiltFields.get("month"));
            assertEquals(fields.get("organization"), rebuiltFields.get("organization"));
            assertEquals(fields.get("title"), rebuiltFields.get("title"));
        }
    }

    @Test
    void roundTrip_stringsBib_preservesLatexTitleIfFileExists() throws Exception {
        Path p = Path.of("strings.bib");
        if (!Files.exists(p)) return;

        String content = Files.readString(p);
        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(content);
        assertFalse(parsed.entries().isEmpty());

        ReferenceVerifier verifier = new ReferenceVerifier();

        boolean foundLatex = false;
        for (BibTeXParser.Entry e : parsed.entries()) {
            if (e.raw().contains("\\text") || e.raw().contains("\\&")) {
                foundLatex = true;
                Map<String, String> fields = verifier._testOnly_parseAllFields(e.raw());
                String title = fields.get("title");
                assertNotNull(title);
                assertFalse(title.isBlank());

                String rebuilt = verifier._testOnly_buildCorrectedEntry(e.type(), e.key(), new ReferenceVerifier.ReferenceData(), e.raw());
                Map<String, String> rebuiltFields = verifier._testOnly_parseAllFields(rebuilt);
                assertEquals(title, rebuiltFields.get("title"));
                break;
            }
        }
        assertTrue(foundLatex);
    }
}

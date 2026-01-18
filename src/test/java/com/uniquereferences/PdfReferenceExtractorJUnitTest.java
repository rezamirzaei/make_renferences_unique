package com.uniquereferences;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfReferenceExtractorJUnitTest {

    @Test
    void extractReferences_numberedFormat() {
        String text = """
                Some content here.
                
                References
                
                [1] Smith, J. (2020). A study on ML. Journal of AI, 15(3), 100-120.
                
                [2] Johnson, A. (2019). Deep learning for NLP. NeurIPS.
                """;

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractReferencesFromText(text);

        assertNotNull(result);
        assertTrue(result.totalFound() > 0, "Should find references");
        assertFalse(result.bibTeXOutput().isBlank(), "Should generate BibTeX output");
    }

    @Test
    void extractReferences_noReferencesSection() {
        String text = "This is a document without any references section.";

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractReferencesFromText(text);

        assertEquals(0, result.totalFound());
        assertTrue(result.messages().stream()
                .anyMatch(m -> m.toLowerCase().contains("could not locate")));
    }

    @Test
    void extractReferences_extractsYear() {
        String text = """
                References
                
                [1] Author Name (2023). Paper title here. Nature, vol. 5.
                """;

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractReferencesFromText(text);

        assertTrue(result.totalFound() >= 1);
        assertTrue(result.bibTeXOutput().contains("2023"), "Year should be extracted");
    }

    @Test
    void extractReferences_bibliographyHeader() {
        String text = """
                Introduction text here.
                
                Bibliography
                
                [1] First Ref (2020). Title One. Journal One.
                
                [2] Second Ref (2021). Title Two. Journal Two.
                """;

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractReferencesFromText(text);

        assertTrue(result.totalFound() >= 1, "Should find references under 'Bibliography' header");
    }

    @Test
    void extractReferences_withDoi() {
        String text = """
                References
                
                [1] Test Author (2020). Test Title. Test Journal. doi: 10.1234/test.2020
                """;

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractReferencesFromText(text);

        assertTrue(result.totalFound() >= 1);
        assertTrue(result.bibTeXOutput().contains("10.1234/test.2020"));
    }

    @Test
    void extractReferences_fromActualPdfFile() throws Exception {
        // Test with the actual PDF file in the project
        Path pdfPath = Path.of("s11081-022-09765-w.pdf");

        if (!Files.exists(pdfPath)) {
            System.out.println("PDF file not found, skipping test: " + pdfPath.toAbsolutePath());
            return; // Skip if file doesn't exist
        }

        PdfReferenceExtractor extractor = new PdfReferenceExtractor();
        var result = extractor.extractFromPdf(pdfPath);

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.bibTeXOutput(), "BibTeX output should not be null");
        assertFalse(result.messages().isEmpty(), "Should have processing messages");

        System.out.println("=== PDF Extraction Results ===");
        System.out.println("Total references found: " + result.totalFound());
        System.out.println("Successfully verified: " + result.successfullyConverted());
        System.out.println("Verification errors: " + result.verificationErrors());
        System.out.println();
        System.out.println("Messages:");
        for (String msg : result.messages()) {
            System.out.println("  - " + msg);
        }

        // Show first 3 BibTeX entries to verify titles are correct
        System.out.println("\n=== First 3 BibTeX entries ===");
        String[] entries = result.bibTeXOutput().split("@");
        for (int i = 1; i < Math.min(4, entries.length); i++) {
            System.out.println("@" + entries[i].substring(0, Math.min(500, entries[i].length())));
            System.out.println("---");
        }

        // Verify no titles start with )
        assertFalse(result.bibTeXOutput().contains("title = {)"),
            "Titles should not start with closing parenthesis");

        // Verify no garbage author names (containing method/statistics words)
        assertFalse(result.bibTeXOutput().contains("author = {Estimation"),
            "Author names should not contain 'Estimation'");
        assertFalse(result.bibTeXOutput().contains("author = {Variance"),
            "Author names should not contain 'Variance'");
        assertFalse(result.bibTeXOutput().contains("Statistical Methods"),
            "Author names should not contain 'Statistical Methods'");

        // We expect around 40-50 references in this paper
        assertTrue(result.totalFound() >= 40, "Should find at least 40 references, found: " + result.totalFound());
        assertTrue(result.successfullyConverted() >= 30, "Should verify at least 30 references");
    }
}





package com.uniquereferences;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts references from PDF files and converts them to BibTeX format.
 *
 * <p>This class:
 * <ul>
 *   <li>Extracts text from PDF files using Apache PDFBox</li>
 *   <li>Identifies the references/bibliography section</li>
 *   <li>Parses individual references</li>
 *   <li>Converts them to BibTeX entries using online APIs for verification</li>
 * </ul>
 */
public class PdfReferenceExtractor {

    private final ReferenceVerifier verifier;

    // Patterns to identify reference sections
    private static final Pattern REFERENCES_HEADER = Pattern.compile(
            "(?i)^\\s*(References|Bibliography|Works\\s+Cited|Literature\\s+Cited|Citations)\\s*$",
            Pattern.MULTILINE
    );

    // Pattern to identify numbered references like [1], [2], etc.
    private static final Pattern NUMBERED_REF_BRACKET = Pattern.compile(
            "^\\s*\\[(\\d+)]\\s*(.+?)(?=^\\s*\\[\\d+]|\\z)",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    // Pattern for "1." or "1 " style numbered references
    private static final Pattern NUMBERED_REF_DOT = Pattern.compile(
            "^\\s*(\\d+)\\.?\\s+([A-Z][^\\n]+(?:\\n(?!\\s*\\d+\\.?\\s+[A-Z])[^\\n]+)*)",
            Pattern.MULTILINE
    );

    // Pattern to identify author-year style references
    private static final Pattern AUTHOR_YEAR_REF = Pattern.compile(
            "^\\s*([A-Z][a-zA-Z'\\-]+(?:,?\\s+(?:and\\s+)?[A-Z][a-zA-Z'\\-]+)*)" +
                    "\\s*[,.]?\\s*\\(?(\\d{4}[a-z]?)\\)?[.,]?\\s*(.+?)(?=^\\s*[A-Z][a-zA-Z'\\-]+(?:,?\\s+(?:and\\s+)?[A-Z]|\\z))",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    // Pattern to extract DOI from text
    private static final Pattern DOI_PATTERN = Pattern.compile(
            "(?:doi[:\\s]*|https?://(?:dx\\.)?doi\\.org/)?(10\\.\\d{4,}/[^\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract year
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    // Pattern to extract title (text in quotes or after year)
    // Supports both straight quotes and Unicode curly quotes
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "[\u201C\u201D\"\u2018\u2019']([^\u201C\u201D\"\u2018\u2019']+)[\u201C\u201D\"\u2018\u2019']|(?:\\d{4}[a-z]?[.,]?\\s*)([^.]+\\.)"
    );

    public PdfReferenceExtractor() {
        this.verifier = new ReferenceVerifier();
    }

    public PdfReferenceExtractor(ReferenceVerifier.VerificationMode mode, MonthNormalizer.MonthStyle monthStyle) {
        this.verifier = new ReferenceVerifier(mode, monthStyle);
    }

    /**
     * Result of PDF reference extraction.
     */
    public record ExtractionResult(
            List<ExtractedReference> references,
            String bibTeXOutput,
            int totalFound,
            int successfullyConverted,
            int verificationErrors,
            List<String> messages
    ) {}

    /**
     * A single extracted reference with its verification status.
     */
    public record ExtractedReference(
            int number,
            String originalText,
            String bibTeXEntry,
            boolean verified,
            String verificationMessage
    ) {}

    /**
     * Extracts references from a PDF file and converts them to BibTeX format.
     *
     * @param pdfPath Path to the PDF file
     * @return ExtractionResult containing the extracted and converted references
     * @throws IOException if the PDF cannot be read
     */
    public ExtractionResult extractFromPdf(Path pdfPath) throws IOException {
        return extractFromPdf(pdfPath.toFile());
    }

    /**
     * Extracts references from a PDF file and converts them to BibTeX format.
     *
     * @param pdfFile The PDF file
     * @return ExtractionResult containing the extracted and converted references
     * @throws IOException if the PDF cannot be read
     */
    public ExtractionResult extractFromPdf(File pdfFile) throws IOException {
        String text = extractTextFromPdf(pdfFile);
        return extractReferencesFromText(text);
    }

    /**
     * Extracts text content from a PDF file.
     */
    private String extractTextFromPdf(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * Extracts references from text (useful for testing without PDF).
     */
    public ExtractionResult extractReferencesFromText(String text) {
        List<String> messages = new ArrayList<>();
        List<ExtractedReference> extractedRefs = new ArrayList<>();

        // Find the references section
        String referencesSection = findReferencesSection(text);
        if (referencesSection == null || referencesSection.isBlank()) {
            messages.add("Could not locate references section in the document.");
            return new ExtractionResult(List.of(), "", 0, 0, 0, messages);
        }

        messages.add("Found references section (" + referencesSection.length() + " characters)");

        // Parse individual references
        List<RawReference> rawRefs = parseReferences(referencesSection);
        messages.add("Parsed " + rawRefs.size() + " raw references");

        if (rawRefs.isEmpty()) {
            messages.add("No references could be parsed from the section.");
            return new ExtractionResult(List.of(), "", 0, 0, 0, messages);
        }

        // Convert each reference to BibTeX
        StringBuilder bibTeXBuilder = new StringBuilder();
        bibTeXBuilder.append("% References extracted from PDF\n");
        bibTeXBuilder.append("% Generated by Unique LaTeX References\n\n");

        int successCount = 0;
        int errorCount = 0;

        for (RawReference raw : rawRefs) {
            ExtractedReference extracted = convertToBibTeX(raw);
            extractedRefs.add(extracted);

            if (extracted.verified()) {
                successCount++;
                bibTeXBuilder.append(extracted.bibTeXEntry()).append("\n\n");
            } else if (extracted.bibTeXEntry() != null && !extracted.bibTeXEntry().isBlank()) {
                // Still include unverified entries with a comment
                bibTeXBuilder.append("% WARNING: Could not verify this reference\n");
                bibTeXBuilder.append(extracted.bibTeXEntry()).append("\n\n");
                errorCount++;
            } else {
                errorCount++;
            }
        }

        messages.add("Successfully converted: " + successCount);
        messages.add("Verification errors: " + errorCount);

        return new ExtractionResult(
                extractedRefs,
                bibTeXBuilder.toString(),
                rawRefs.size(),
                successCount,
                errorCount,
                messages
        );
    }

    /**
     * Finds and extracts the references section from the document text.
     */
    private String findReferencesSection(String text) {
        Matcher headerMatcher = REFERENCES_HEADER.matcher(text);
        if (headerMatcher.find()) {
            int start = headerMatcher.end();
            // Look for the end of references (next major section or end of document)
            Pattern nextSection = Pattern.compile(
                    "(?i)^\\s*(Appendix|Acknowledgments?|About\\s+the\\s+Authors?|Author\\s+Bio|Supplementary)\\s*$",
                    Pattern.MULTILINE
            );
            Matcher nextMatcher = nextSection.matcher(text);
            int end = text.length();
            if (nextMatcher.find(start)) {
                end = nextMatcher.start();
            }
            return text.substring(start, end).trim();
        }

        // Fallback: look for a section with many citations
        // Try to find dense citation patterns near the end of the document
        int lastThird = text.length() * 2 / 3;
        String endPart = text.substring(lastThird);

        // Check if it has citation patterns
        Matcher numMatcher = NUMBERED_REF_BRACKET.matcher(endPart);
        int citationCount = 0;
        while (numMatcher.find() && citationCount < 5) {
            citationCount++;
        }

        if (citationCount >= 3) {
            return endPart;
        }

        return null;
    }

    /**
     * Raw reference data before conversion.
     */
    private record RawReference(int number, String text, String doi, String year, String possibleTitle) {}

    /**
     * Parses individual references from the references section.
     * Tries multiple strategies to handle different reference formats.
     */
    private List<RawReference> parseReferences(String section) {
        List<RawReference> refs;

        // Strategy 1: Try bracket-numbered references [1], [2], etc.
        refs = parseNumberedBracketRefs(section);
        if (refs.size() >= 5) {
            return refs;
        }

        // Strategy 2: Try dot-numbered references: "1. Author..."
        refs = parseNumberedDotRefs(section);
        if (refs.size() >= 5) {
            return refs;
        }

        // Strategy 3: Try plain numbered references: "1 Author..." (common in Springer papers)
        refs = parsePlainNumberedRefs(section);
        if (refs.size() >= 5) {
            return refs;
        }

        // Strategy 4: Split by blank lines (author-year style)
        refs = parseByBlankLines(section);
        if (refs.size() >= 3) {
            return refs;
        }

        // Strategy 5: Split by author name patterns
        refs = parseByAuthorPatterns(section);

        return refs;
    }

    private List<RawReference> parseNumberedBracketRefs(String section) {
        List<RawReference> refs = new ArrayList<>();
        Matcher m = NUMBERED_REF_BRACKET.matcher(section);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            String refText = m.group(2).trim();
            if (refText.length() > 20) {
                refs.add(parseReferenceDetails(num, refText));
            }
        }
        return refs;
    }

    private List<RawReference> parseNumberedDotRefs(String section) {
        List<RawReference> refs = new ArrayList<>();
        // Pattern: number followed by dot, then text until next number-dot or end
        Pattern p = Pattern.compile("(?:^|\\n)\\s*(\\d{1,3})\\.\\s+(.+?)(?=\\n\\s*\\d{1,3}\\.\\s|$)", Pattern.DOTALL);
        Matcher m = p.matcher(section);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            String refText = m.group(2).trim().replaceAll("\\s+", " ");
            if (refText.length() > 30) {
                refs.add(parseReferenceDetails(num, refText));
            }
        }
        return refs;
    }

    private List<RawReference> parsePlainNumberedRefs(String section) {
        List<RawReference> refs = new ArrayList<>();
        // Pattern for Springer-style: number at start of line followed by author name
        // e.g., "1. Anderson TW (1973) Asymptotically..."
        // or "1 Anderson TW (1973)..."
        Pattern p = Pattern.compile("(?:^|\\n)\\s*(\\d{1,3})\\.?\\s+([A-Z][a-zA-Z]+[^\\n]*(?:\\n(?!\\s*\\d{1,3}\\.?\\s+[A-Z])[^\\n]*)*)", Pattern.MULTILINE);
        Matcher m = p.matcher(section);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            String refText = m.group(2).trim().replaceAll("\\s+", " ");
            if (refText.length() > 30) {
                refs.add(parseReferenceDetails(num, refText));
            }
        }
        return refs;
    }

    private List<RawReference> parseByBlankLines(String section) {
        List<RawReference> refs = new ArrayList<>();
        // Split by one or more blank lines
        String[] blocks = section.split("\\n\\s*\\n+");
        int refNum = 1;
        for (String block : blocks) {
            String text = block.trim().replaceAll("\\s+", " ");
            // Must look like a reference (has year, reasonable length)
            if (text.length() > 40 && YEAR_PATTERN.matcher(text).find()) {
                refs.add(parseReferenceDetails(refNum++, text));
            }
        }
        return refs;
    }

    private List<RawReference> parseByAuthorPatterns(String section) {
        List<RawReference> refs = new ArrayList<>();
        // Split when we see a new author name pattern at start of line
        // Author patterns: "LastName AB", "LastName, A.B.", "LastName A, "
        String[] lines = section.split("\\n");
        StringBuilder currentRef = new StringBuilder();
        int refNum = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Check if this line starts a new reference
            boolean isNewRef = false;
            // Starts with number followed by author
            if (trimmed.matches("^\\d{1,3}\\.?\\s+[A-Z][a-z]+.*")) {
                isNewRef = true;
            }
            // Starts with author name pattern (LastName followed by initials or comma)
            else if (trimmed.matches("^[A-Z][a-z]+\\s+[A-Z]{1,2}[,.]?\\s.*") ||
                     trimmed.matches("^[A-Z][a-z]+,\\s+[A-Z].*")) {
                isNewRef = true;
            }

            if (isNewRef && currentRef.length() > 40) {
                refs.add(parseReferenceDetails(refNum++, currentRef.toString().trim().replaceAll("\\s+", " ")));
                currentRef = new StringBuilder();
            }

            if (currentRef.length() > 0) {
                currentRef.append(" ");
            }
            currentRef.append(trimmed);
        }

        // Don't forget the last reference
        if (currentRef.length() > 40) {
            refs.add(parseReferenceDetails(refNum, currentRef.toString().trim().replaceAll("\\s+", " ")));
        }

        return refs;
    }

    /**
     * Extracts details (DOI, year, title) from reference text.
     */
    private RawReference parseReferenceDetails(int number, String text) {
        // Clean up the text
        String cleanText = text.replaceAll("\\s+", " ").trim();

        // Extract DOI if present
        String doi = null;
        Matcher doiMatcher = DOI_PATTERN.matcher(cleanText);
        if (doiMatcher.find()) {
            doi = doiMatcher.group(1);
            // Clean trailing punctuation from DOI
            doi = doi.replaceAll("[.,;:\\s]+$", "");
        }

        // Extract year
        String year = null;
        Matcher yearMatcher = YEAR_PATTERN.matcher(cleanText);
        if (yearMatcher.find()) {
            year = yearMatcher.group(0);
        }

        // Try to extract title
        String title = null;
        Matcher titleMatcher = TITLE_PATTERN.matcher(cleanText);
        if (titleMatcher.find()) {
            title = titleMatcher.group(1) != null ? titleMatcher.group(1) : titleMatcher.group(2);
            if (title != null) {
                title = title.trim().replaceAll("\\.$", "");
            }
        }

        // Clean up title - remove leading punctuation like ), ], etc.
        if (title != null) {
            title = title.replaceAll("^[)\\]}>.,;:\\s]+", "").trim();
        }

        return new RawReference(number, cleanText, doi, year, title);
    }

    /**
     * Converts a raw reference to BibTeX format, attempting verification.
     */
    private ExtractedReference convertToBibTeX(RawReference raw) {
        // First, try to verify/lookup using DOI or title
        String searchQuery = raw.doi() != null ? raw.doi() : raw.possibleTitle();

        if (searchQuery == null || searchQuery.isBlank()) {
            // Can't verify, create a basic entry
            String basicEntry = createBasicBibTeXEntry(raw);
            return new ExtractedReference(
                    raw.number(),
                    raw.text(),
                    basicEntry,
                    false,
                    "Could not extract enough information to verify"
            );
        }

        // Create a temporary BibTeX entry for verification
        String tempEntry = createTempBibTeXEntry(raw);

        try {
            ReferenceVerifier.VerificationResult result = verifier.verify(tempEntry);

            if (result.status() == ReferenceVerifier.VerificationStatus.VERIFIED ||
                    result.status() == ReferenceVerifier.VerificationStatus.CORRECTED) {
                return new ExtractedReference(
                        raw.number(),
                        raw.text(),
                        result.correctedEntry(),
                        true,
                        result.message()
                );
            } else {
                // Verification failed, return basic entry
                String basicEntry = createBasicBibTeXEntry(raw);
                return new ExtractedReference(
                        raw.number(),
                        raw.text(),
                        basicEntry,
                        false,
                        result.message()
                );
            }
        } catch (Exception e) {
            String basicEntry = createBasicBibTeXEntry(raw);
            return new ExtractedReference(
                    raw.number(),
                    raw.text(),
                    basicEntry,
                    false,
                    "Verification error: " + e.getMessage()
            );
        }
    }

    /**
     * Creates a temporary BibTeX entry for verification lookup.
     */
    private String createTempBibTeXEntry(RawReference raw) {
        StringBuilder sb = new StringBuilder();
        sb.append("@misc{ref").append(raw.number()).append(",\n");

        if (raw.possibleTitle() != null) {
            sb.append("  title = {").append(escapeBibTeX(raw.possibleTitle())).append("},\n");
        }
        if (raw.doi() != null) {
            sb.append("  doi = {").append(raw.doi()).append("},\n");
        }
        if (raw.year() != null) {
            sb.append("  year = {").append(raw.year()).append("},\n");
        }
        sb.append("  note = {Extracted from PDF}\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * Creates a basic BibTeX entry when verification is not possible.
     */
    private String createBasicBibTeXEntry(RawReference raw) {
        StringBuilder sb = new StringBuilder();
        String key = generateKey(raw);

        sb.append("@misc{").append(key).append(",\n");

        if (raw.possibleTitle() != null) {
            sb.append("  title = {").append(escapeBibTeX(raw.possibleTitle())).append("},\n");
        } else {
            // Use first part of text as title
            String shortText = raw.text();
            if (shortText.length() > 100) {
                shortText = shortText.substring(0, 100) + "...";
            }
            sb.append("  title = {").append(escapeBibTeX(shortText)).append("},\n");
        }

        if (raw.year() != null) {
            sb.append("  year = {").append(raw.year()).append("},\n");
        }
        if (raw.doi() != null) {
            sb.append("  doi = {").append(raw.doi()).append("},\n");
        }

        sb.append("  note = {Extracted from PDF - needs manual verification}\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * Generates a BibTeX key from reference information.
     */
    private String generateKey(RawReference raw) {
        StringBuilder key = new StringBuilder();

        // Try to extract first author's last name
        String text = raw.text();
        Matcher authorMatcher = Pattern.compile("^([A-Z][a-z]+)").matcher(text);
        if (authorMatcher.find()) {
            key.append(authorMatcher.group(1).toLowerCase());
        } else {
            key.append("ref");
        }

        if (raw.year() != null) {
            key.append(raw.year());
        } else {
            key.append(raw.number());
        }

        // Add a word from title if available
        if (raw.possibleTitle() != null) {
            String[] words = raw.possibleTitle().split("\\s+");
            for (String word : words) {
                if (word.length() > 4 && word.matches("[A-Za-z]+")) {
                    key.append(word.toLowerCase());
                    break;
                }
            }
        }

        return key.toString();
    }

    /**
     * Escapes special BibTeX characters.
     */
    private String escapeBibTeX(String text) {
        if (text == null) return "";
        return text
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\~{}")
                .replace("^", "\\^{}");
    }
}

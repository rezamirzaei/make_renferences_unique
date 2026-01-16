package com.uniquereferences;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies and corrects BibTeX references using online sources (CrossRef API).
 * <p>
 * CrossRef is a free API that provides metadata for academic publications.
 */
public class ReferenceVerifier {

    private static final String CROSSREF_API = "https://api.crossref.org/works";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Patterns to extract fields from BibTeX
    private static final Pattern DOI_PATTERN = Pattern.compile("doi\\s*=\\s*[{\"](10\\.[^}\"]+)[}\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("title\\s*=\\s*[{\"]+([^}\"]+)[}\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("author\\s*=\\s*[{\"]+([^}\"]+)[}\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("year\\s*=\\s*[{\"]*([0-9]{4})[}\"]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOURNAL_PATTERN = Pattern.compile("journal\\s*=\\s*[{\"]+([^}\"]+)[}\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOLUME_PATTERN = Pattern.compile("volume\\s*=\\s*[{\"]*([^},\"]+)[}\"]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGES_PATTERN = Pattern.compile("pages\\s*=\\s*[{\"]*([^},\"]+)[}\"]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTRY_TYPE_KEY = Pattern.compile("@(\\w+)\\s*\\{\\s*([^,]+),", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public ReferenceVerifier() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Result of verifying a reference.
     */
    public record VerificationResult(
            String originalEntry,
            String correctedEntry,
            VerificationStatus status,
            String message
    ) {}

    public enum VerificationStatus {
        VERIFIED,           // Reference is correct
        CORRECTED,          // Reference was corrected/completed
        NOT_FOUND,          // Could not find in online sources
        ERROR,              // Error during verification
        SKIPPED             // Skipped (e.g., no searchable info)
    }

    /**
     * Verifies and potentially corrects a single BibTeX entry.
     */
    public VerificationResult verify(String bibEntry) {
        try {
            // Extract DOI if present
            String doi = extractField(bibEntry, DOI_PATTERN);
            if (doi != null && !doi.isEmpty()) {
                return verifyByDoi(bibEntry, doi);
            }

            // Otherwise, search by title
            String title = extractField(bibEntry, TITLE_PATTERN);
            if (title != null && !title.isEmpty()) {
                return verifyByTitle(bibEntry, title);
            }

            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.SKIPPED,
                    "No DOI or title found to verify");

        } catch (Exception e) {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.ERROR,
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Verifies a reference by DOI lookup.
     */
    private VerificationResult verifyByDoi(String bibEntry, String doi) {
        try {
            String url = CROSSREF_API + "/" + URLEncoder.encode(doi, StandardCharsets.UTF_8);
            Optional<String> response = fetchUrl(url);

            if (response.isEmpty()) {
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.NOT_FOUND,
                        "DOI not found in CrossRef");
            }

            return processResponse(bibEntry, response.get());

        } catch (Exception e) {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.ERROR,
                    "Error looking up DOI: " + e.getMessage());
        }
    }

    /**
     * Verifies a reference by title search.
     */
    private VerificationResult verifyByTitle(String bibEntry, String title) {
        try {
            String cleanTitle = title.replaceAll("[{}]", "").trim();
            String url = CROSSREF_API + "?query.title=" +
                    URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8) +
                    "&rows=1";

            Optional<String> response = fetchUrl(url);

            if (response.isEmpty()) {
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.NOT_FOUND,
                        "Title not found in CrossRef");
            }

            // Check if we got any results
            if (!response.get().contains("\"items\":[{")) {
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.NOT_FOUND,
                        "No matching references found");
            }

            return processResponse(bibEntry, response.get());

        } catch (Exception e) {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.ERROR,
                    "Error searching by title: " + e.getMessage());
        }
    }

    /**
     * Processes the CrossRef API response and creates a corrected entry.
     */
    private VerificationResult processResponse(String bibEntry, String json) {
        try {
            CrossRefData data = parseJson(json);

            if (data == null) {
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.NOT_FOUND,
                        "Could not parse response");
            }

            // Extract original key and type
            Matcher m = ENTRY_TYPE_KEY.matcher(bibEntry);
            String entryType = "article";
            String key = "unknown";
            if (m.find()) {
                entryType = m.group(1).toLowerCase();
                key = m.group(2).trim();
            }

            // Determine the correct entry type
            if (data.type != null) {
                entryType = mapCrossRefType(data.type);
            }

            // Build corrected entry
            String corrected = buildCorrectedEntry(entryType, key, data, bibEntry);

            // Check if anything changed
            boolean changed = !normalizeForComparison(corrected).equals(normalizeForComparison(bibEntry));

            if (changed) {
                return new VerificationResult(bibEntry, corrected, VerificationStatus.CORRECTED,
                        "Reference corrected/completed from CrossRef");
            } else {
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.VERIFIED,
                        "Reference verified");
            }

        } catch (Exception e) {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.ERROR,
                    "Error processing response: " + e.getMessage());
        }
    }

    /**
     * Simple JSON parsing for CrossRef response (avoiding external dependencies).
     */
    private CrossRefData parseJson(String json) {
        CrossRefData data = new CrossRefData();

        // Handle both single item (DOI lookup) and search results
        String messageBlock = json;
        if (json.contains("\"items\":[{")) {
            // Extract first item from search results
            int itemsStart = json.indexOf("\"items\":[{") + 9;
            int depth = 0;
            int itemEnd = itemsStart;
            for (int i = itemsStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        itemEnd = i + 1;
                        break;
                    }
                }
            }
            messageBlock = json.substring(itemsStart, itemEnd);
        }

        data.title = extractJsonString(messageBlock, "title");
        data.doi = extractJsonString(messageBlock, "DOI");
        data.type = extractJsonString(messageBlock, "type");
        data.containerTitle = extractJsonString(messageBlock, "container-title");
        data.volume = extractJsonString(messageBlock, "volume");
        data.issue = extractJsonString(messageBlock, "issue");
        data.page = extractJsonString(messageBlock, "page");
        data.publisher = extractJsonString(messageBlock, "publisher");
        data.authors = extractAuthors(messageBlock);
        data.year = extractYear(messageBlock);
        data.issn = extractJsonString(messageBlock, "ISSN");
        data.url = extractJsonString(messageBlock, "URL");

        return data;
    }

    private String extractJsonString(String json, String key) {
        // Try array format first: "key":["value"]
        // Supports escaped quotes within the string: ((?:[^"\\]|\\.)*)
        Pattern arrayPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = arrayPattern.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }

        // Try simple string format: "key":"value"
        Pattern simplePattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE);
        m = simplePattern.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }

        // Try number format: "key":123
        Pattern numberPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
        m = numberPattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String extractAuthors(String json) {
        StringBuilder authors = new StringBuilder();

        // Find author array
        Pattern authorPattern = Pattern.compile("\"author\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = authorPattern.matcher(json);
        if (m.find()) {
            String authorArray = m.group(1);

            // Extract each author's given and family name
            Pattern namePattern = Pattern.compile("\"family\"\\s*:\\s*\"([^\"]+)\"[^}]*\"given\"\\s*:\\s*\"([^\"]+)\"");
            Matcher nm = namePattern.matcher(authorArray);
            while (nm.find()) {
                if (!authors.isEmpty()) authors.append(" and ");
                authors.append(nm.group(1)).append(", ").append(nm.group(2));
            }

            // Try reverse order (given before family)
            if (authors.isEmpty()) {
                namePattern = Pattern.compile("\"given\"\\s*:\\s*\"([^\"]+)\"[^}]*\"family\"\\s*:\\s*\"([^\"]+)\"");
                nm = namePattern.matcher(authorArray);
                while (nm.find()) {
                    if (!authors.isEmpty()) authors.append(" and ");
                    authors.append(nm.group(2)).append(", ").append(nm.group(1));
                }
            }
        }

        return !authors.isEmpty() ? authors.toString() : null;
    }

    private String extractYear(String json) {
        // Look for published-print or published-online date
        Pattern datePattern = Pattern.compile("\"published(?:-print|-online)?\"\\s*:\\s*\\{[^}]*\"date-parts\"\\s*:\\s*\\[\\s*\\[\\s*(\\d{4})");
        Matcher m = datePattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        // Fallback: look for issued date
        datePattern = Pattern.compile("\"issued\"\\s*:\\s*\\{[^}]*\"date-parts\"\\s*:\\s*\\[\\s*\\[\\s*(\\d{4})");
        m = datePattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Maps CrossRef type to BibTeX entry type.
     */
    private String mapCrossRefType(String crossRefType) {
        return switch (crossRefType.toLowerCase()) {
            case "journal-article" -> "article";
            case "book" -> "book";
            case "book-chapter" -> "incollection";
            case "proceedings-article" -> "inproceedings";
            case "dissertation" -> "phdthesis";
            case "report" -> "techreport";
            default -> "misc";
        };
    }

    /**
     * Builds a corrected BibTeX entry.
     */
    private String buildCorrectedEntry(String type, String key, CrossRefData data, String original) {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(type).append("{").append(key).append(",\n");

        // Use data from CrossRef, falling back to original if not available
        appendField(sb, "author", data.authors, extractField(original, AUTHOR_PATTERN));
        appendField(sb, "title", data.title, extractField(original, TITLE_PATTERN));
        appendField(sb, "journal", data.containerTitle, extractField(original, JOURNAL_PATTERN));
        appendField(sb, "year", data.year, extractField(original, YEAR_PATTERN));
        appendField(sb, "volume", data.volume, extractField(original, VOLUME_PATTERN));

        if (data.issue != null) {
            sb.append("  number = {").append(data.issue).append("},\n");
        }

        appendField(sb, "pages", data.page, extractField(original, PAGES_PATTERN));

        if (data.doi != null) {
            sb.append("  doi = {").append(data.doi).append("},\n");
        }

        if (data.url != null && data.doi == null) {
            sb.append("  url = {").append(data.url).append("},\n");
        }

        if (data.publisher != null && (type.equals("book") || type.equals("incollection"))) {
            sb.append("  publisher = {").append(data.publisher).append("},\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String name, String crossRefValue, String originalValue) {
        String value = crossRefValue != null && !crossRefValue.isEmpty() ? crossRefValue : originalValue;
        if (value != null && !value.isEmpty()) {
            // Clean up the value
            value = value.replaceAll("[{}]", "").trim();
            sb.append("  ").append(name).append(" = {").append(value).append("},\n");
        }
    }

    private String extractField(String bibEntry, Pattern pattern) {
        Matcher m = pattern.matcher(bibEntry);
        return m.find() ? m.group(1) : null;
    }

    private String normalizeForComparison(String s) {
        return s.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[{}]", "")
                .trim();
    }

    private Optional<String> fetchUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "UniqueReferencesApp/1.0 (mailto:user@example.com)")
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return Optional.of(response.body());
        } else if (response.statusCode() == 404) {
            return Optional.empty();
        } else {
            throw new IOException("HTTP error: " + response.statusCode());
        }
    }

    /**
     * Helper class to hold parsed CrossRef data.
     */
    private static class CrossRefData {
        String title;
        String doi;
        String type;
        String containerTitle; // journal name
        String volume;
        String issue;
        String page;
        String publisher;
        String authors;
        String year;
        String issn;
        String url;
    }
}

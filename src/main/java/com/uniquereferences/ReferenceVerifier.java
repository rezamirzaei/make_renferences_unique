package com.uniquereferences;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies and corrects BibTeX references using multiple online sources:
 * - CrossRef (primary - largest academic database)
 * - Semantic Scholar (good for CS papers)
 * - OpenAlex (open access academic data)
 *
 * The verifier queries all available sources and selects the most complete result.
 */
public class ReferenceVerifier {

    // API endpoints
    private static final String CROSSREF_API = "https://api.crossref.org/works";
    private static final String SEMANTIC_SCHOLAR_API = "https://api.semanticscholar.org/graph/v1/paper";
    private static final String OPENALEX_API = "https://api.openalex.org/works";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Month name mappings
    private static final String[] MONTH_NAMES = {
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    };
    private static final String[] MONTH_FULL_NAMES = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

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
     * Queries multiple sources and selects the best result.
     */
    public VerificationResult verify(String bibEntry) {
        try {
            String doi = BibTeXParser.extractField(bibEntry, "doi");
            String title = BibTeXParser.extractField(bibEntry, "title");
            String author = BibTeXParser.extractField(bibEntry, "author");
            String year = BibTeXParser.extractField(bibEntry, "year");

            List<ReferenceData> results = new ArrayList<>();

            // Strategy 1: Search by DOI (most accurate)
            if (doi != null && !doi.isEmpty()) {
                // Try CrossRef
                ReferenceData crossRefResult = fetchFromCrossRef(doi, null);
                if (crossRefResult != null) results.add(crossRefResult);

                // Try Semantic Scholar
                ReferenceData ssResult = fetchFromSemanticScholar(doi, null);
                if (ssResult != null) results.add(ssResult);

                // Try OpenAlex
                ReferenceData oaResult = fetchFromOpenAlex(doi, null);
                if (oaResult != null) results.add(oaResult);
            }

            // Strategy 2: Search by title if DOI didn't give good results
            if (results.isEmpty() && title != null && !title.isEmpty() && title.length() > 10) {
                String cleanTitle = title.replaceAll("[{}]", "").trim();

                ReferenceData crossRefResult = fetchFromCrossRef(null, cleanTitle);
                if (crossRefResult != null) results.add(crossRefResult);

                ReferenceData ssResult = fetchFromSemanticScholar(null, cleanTitle);
                if (ssResult != null) results.add(ssResult);

                ReferenceData oaResult = fetchFromOpenAlex(null, cleanTitle);
                if (oaResult != null) results.add(oaResult);
            }

            // Strategy 3: Search by author + year
            if (results.isEmpty() && author != null && !author.isEmpty() && year != null) {
                ReferenceData crossRefResult = fetchFromCrossRefByAuthorYear(author, year, title);
                if (crossRefResult != null) results.add(crossRefResult);
            }

            // No results found
            if (results.isEmpty()) {
                if (title != null && !title.isEmpty()) {
                    return new VerificationResult(bibEntry, bibEntry, VerificationStatus.NOT_FOUND,
                            "Could not find reference in any online source");
                }
                return new VerificationResult(bibEntry, bibEntry, VerificationStatus.SKIPPED,
                        "No DOI, title, or author+year found to verify");
            }

            // Select the best result (most complete)
            ReferenceData best = selectBestResult(results);

            // Build corrected entry
            return buildResult(bibEntry, best);

        } catch (Exception e) {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.ERROR,
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Fetches reference data from CrossRef API.
     */
    private ReferenceData fetchFromCrossRef(String doi, String title) {
        try {
            String url;
            if (doi != null) {
                url = CROSSREF_API + "/" + URLEncoder.encode(doi, StandardCharsets.UTF_8);
            } else {
                url = CROSSREF_API + "?query.title=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&rows=1";
            }

            Optional<String> response = fetchUrl(url);
            if (response.isEmpty()) return null;

            String json = response.get();
            if (title != null && !json.contains("\"items\":[{")) return null;

            return parseCrossRefJson(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches reference data from CrossRef by author and year.
     */
    private ReferenceData fetchFromCrossRefByAuthorYear(String author, String year, String title) {
        try {
            String authorName = author.replaceAll("[{}]", "").trim();
            if (authorName.contains(",")) {
                authorName = authorName.split(",")[0].trim();
            } else if (authorName.contains(" and ")) {
                authorName = authorName.split(" and ")[0].trim();
                String[] parts = authorName.split(" ");
                authorName = parts[parts.length - 1];
            }

            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append(CROSSREF_API).append("?query.author=");
            queryBuilder.append(URLEncoder.encode(authorName, StandardCharsets.UTF_8));
            queryBuilder.append("&filter=from-pub-date:").append(year);
            queryBuilder.append(",until-pub-date:").append(year);

            if (title != null && title.length() > 5) {
                String shortTitle = title.replaceAll("[{}]", "").trim();
                if (shortTitle.length() > 50) shortTitle = shortTitle.substring(0, 50);
                queryBuilder.append("&query.title=");
                queryBuilder.append(URLEncoder.encode(shortTitle, StandardCharsets.UTF_8));
            }
            queryBuilder.append("&rows=3");

            Optional<String> response = fetchUrl(queryBuilder.toString());
            if (response.isEmpty() || !response.get().contains("\"items\":[{")) return null;

            return parseCrossRefJson(response.get());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches reference data from Semantic Scholar API.
     */
    private ReferenceData fetchFromSemanticScholar(String doi, String title) {
        try {
            String url;
            if (doi != null) {
                url = SEMANTIC_SCHOLAR_API + "/DOI:" + URLEncoder.encode(doi, StandardCharsets.UTF_8) +
                      "?fields=title,authors,year,venue,publicationDate,externalIds,journal,volume,pages";
            } else {
                url = SEMANTIC_SCHOLAR_API + "/search?query=" + URLEncoder.encode(title, StandardCharsets.UTF_8) +
                      "&fields=title,authors,year,venue,publicationDate,externalIds,journal,volume,pages&limit=1";
            }

            Optional<String> response = fetchUrl(url);
            if (response.isEmpty()) return null;

            return parseSemanticScholarJson(response.get(), doi == null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches reference data from OpenAlex API.
     */
    private ReferenceData fetchFromOpenAlex(String doi, String title) {
        try {
            String url;
            if (doi != null) {
                url = OPENALEX_API + "/https://doi.org/" + doi;
            } else {
                url = OPENALEX_API + "?filter=title.search:" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&per_page=1";
            }

            Optional<String> response = fetchUrl(url);
            if (response.isEmpty()) return null;

            return parseOpenAlexJson(response.get(), doi == null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses CrossRef API JSON response.
     */
    private ReferenceData parseCrossRefJson(String json) {
        ReferenceData data = new ReferenceData();
        data.source = "CrossRef";

        String messageBlock = json;
        if (json.contains("\"items\":[{")) {
            int itemsStart = json.indexOf("\"items\":[{") + 9;
            int depth = 0;
            int itemEnd = itemsStart;
            for (int i = itemsStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { itemEnd = i + 1; break; }
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
        data.pages = extractJsonString(messageBlock, "page");
        data.publisher = extractJsonString(messageBlock, "publisher");
        data.authors = extractAuthorsFromCrossRef(messageBlock);
        data.issn = extractJsonString(messageBlock, "ISSN");
        data.isbn = extractJsonString(messageBlock, "ISBN");
        data.url = extractJsonString(messageBlock, "URL");

        // Extract year and month from published date
        extractDateFromCrossRef(messageBlock, data);

        return data;
    }

    /**
     * Parses Semantic Scholar API JSON response.
     */
    private ReferenceData parseSemanticScholarJson(String json, boolean isSearch) {
        try {
            ReferenceData data = new ReferenceData();
            data.source = "Semantic Scholar";

            String block = json;
            if (isSearch && json.contains("\"data\":[{")) {
                int start = json.indexOf("\"data\":[{") + 8;
                int depth = 0;
                int end = start;
                for (int i = start; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) { end = i + 1; break; }
                    }
                }
                block = json.substring(start, end);
            }

            data.title = extractJsonString(block, "title");
            data.year = extractJsonString(block, "year");
            data.containerTitle = extractJsonString(block, "venue");

            // Extract DOI from externalIds
            if (block.contains("\"DOI\"")) {
                Pattern p = Pattern.compile("\"DOI\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(block);
                if (m.find()) data.doi = m.group(1);
            }

            // Extract authors
            data.authors = extractAuthorsFromSemanticScholar(block);

            // Extract publication date for month
            String pubDate = extractJsonString(block, "publicationDate");
            if (pubDate != null && pubDate.length() >= 7) {
                try {
                    int month = Integer.parseInt(pubDate.substring(5, 7));
                    if (month >= 1 && month <= 12) {
                        data.month = MONTH_FULL_NAMES[month - 1];
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Journal info
            if (block.contains("\"journal\"")) {
                String journalName = extractNestedJsonString(block, "journal", "name");
                if (journalName != null) data.containerTitle = journalName;
                data.volume = extractNestedJsonString(block, "journal", "volume");
                data.pages = extractNestedJsonString(block, "journal", "pages");
            }

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses OpenAlex API JSON response.
     */
    private ReferenceData parseOpenAlexJson(String json, boolean isSearch) {
        try {
            ReferenceData data = new ReferenceData();
            data.source = "OpenAlex";

            String block = json;
            if (isSearch && json.contains("\"results\":[{")) {
                int start = json.indexOf("\"results\":[{") + 11;
                int depth = 0;
                int end = start;
                for (int i = start; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) { end = i + 1; break; }
                    }
                }
                block = json.substring(start, end);
            }

            data.title = extractJsonString(block, "title");
            data.doi = extractJsonString(block, "doi");
            if (data.doi != null && data.doi.startsWith("https://doi.org/")) {
                data.doi = data.doi.substring(16);
            }
            data.type = extractJsonString(block, "type");

            // Publication date
            String pubDate = extractJsonString(block, "publication_date");
            if (pubDate != null && pubDate.length() >= 4) {
                data.year = pubDate.substring(0, 4);
                if (pubDate.length() >= 7) {
                    try {
                        int month = Integer.parseInt(pubDate.substring(5, 7));
                        if (month >= 1 && month <= 12) {
                            data.month = MONTH_FULL_NAMES[month - 1];
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Extract authors from authorships
            data.authors = extractAuthorsFromOpenAlex(block);

            // Source/journal info
            if (block.contains("\"primary_location\"")) {
                String sourceName = extractNestedJsonString(block, "source", "display_name");
                if (sourceName != null) data.containerTitle = sourceName;
            }

            // Biblio info
            data.volume = extractJsonString(block, "volume");
            data.issue = extractJsonString(block, "issue");
            String firstPage = extractJsonString(block, "first_page");
            String lastPage = extractJsonString(block, "last_page");
            if (firstPage != null) {
                data.pages = lastPage != null ? firstPage + "-" + lastPage : firstPage;
            }

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts date (year and month) from CrossRef JSON.
     */
    private void extractDateFromCrossRef(String json, ReferenceData data) {
        // Try published-print first, then published-online, then issued
        String[] dateFields = {"published-print", "published-online", "published", "issued"};

        for (String field : dateFields) {
            Pattern datePattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\\{[^}]*\"date-parts\"\\s*:\\s*\\[\\s*\\[\\s*(\\d{4})(?:\\s*,\\s*(\\d{1,2}))?");
            Matcher m = datePattern.matcher(json);
            if (m.find()) {
                data.year = m.group(1);
                if (m.group(2) != null) {
                    try {
                        int month = Integer.parseInt(m.group(2));
                        if (month >= 1 && month <= 12) {
                            data.month = MONTH_FULL_NAMES[month - 1];
                        }
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
        }
    }

    /**
     * Extracts authors from CrossRef JSON.
     */
    private String extractAuthorsFromCrossRef(String json) {
        StringBuilder authors = new StringBuilder();
        Pattern authorPattern = Pattern.compile("\"author\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = authorPattern.matcher(json);
        if (m.find()) {
            String authorArray = m.group(1);
            Pattern namePattern = Pattern.compile("\"family\"\\s*:\\s*\"([^\"]+)\"[^}]*\"given\"\\s*:\\s*\"([^\"]+)\"");
            Matcher nm = namePattern.matcher(authorArray);
            while (nm.find()) {
                if (!authors.isEmpty()) authors.append(" and ");
                authors.append(nm.group(1)).append(", ").append(nm.group(2));
            }
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

    /**
     * Extracts authors from Semantic Scholar JSON.
     */
    private String extractAuthorsFromSemanticScholar(String json) {
        StringBuilder authors = new StringBuilder();
        Pattern p = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String name = m.group(1);
            if (name.contains(" ")) {
                if (!authors.isEmpty()) authors.append(" and ");
                // Convert "First Last" to "Last, First"
                String[] parts = name.split(" ");
                if (parts.length >= 2) {
                    authors.append(parts[parts.length - 1]).append(", ");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (i > 0) authors.append(" ");
                        authors.append(parts[i]);
                    }
                } else {
                    authors.append(name);
                }
            }
        }
        return !authors.isEmpty() ? authors.toString() : null;
    }

    /**
     * Extracts authors from OpenAlex JSON.
     */
    private String extractAuthorsFromOpenAlex(String json) {
        StringBuilder authors = new StringBuilder();
        Pattern p = Pattern.compile("\"display_name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        int count = 0;
        while (m.find() && count < 20) { // Limit to avoid non-author matches
            String name = m.group(1);
            // Skip if it looks like a source/journal name
            if (name.length() > 50 || name.contains("Journal") || name.contains("Conference")) continue;
            if (name.contains(" ") && !name.contains("University") && !name.contains("Institute")) {
                if (!authors.isEmpty()) authors.append(" and ");
                String[] parts = name.split(" ");
                if (parts.length >= 2) {
                    authors.append(parts[parts.length - 1]).append(", ");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (i > 0) authors.append(" ");
                        authors.append(parts[i]);
                    }
                } else {
                    authors.append(name);
                }
                count++;
            }
        }
        return !authors.isEmpty() ? authors.toString() : null;
    }

    /**
     * Selects the best (most complete) result from multiple sources.
     */
    private ReferenceData selectBestResult(List<ReferenceData> results) {
        ReferenceData best = null;
        int bestScore = -1;

        for (ReferenceData data : results) {
            int score = calculateCompletenessScore(data);
            if (score > bestScore) {
                bestScore = score;
                best = data;
            }
        }

        // Merge data from all sources to get the most complete result
        if (best != null && results.size() > 1) {
            for (ReferenceData other : results) {
                if (other != best) {
                    mergeData(best, other);
                }
            }
        }

        return best;
    }

    /**
     * Calculates a completeness score for ranking results.
     */
    private int calculateCompletenessScore(ReferenceData data) {
        int score = 0;
        if (data.title != null && !data.title.isEmpty()) score += 10;
        if (data.authors != null && !data.authors.isEmpty()) score += 10;
        if (data.year != null && !data.year.isEmpty()) score += 5;
        if (data.month != null && !data.month.isEmpty()) score += 5;
        if (data.containerTitle != null && !data.containerTitle.isEmpty()) score += 5;
        if (data.volume != null && !data.volume.isEmpty()) score += 3;
        if (data.issue != null && !data.issue.isEmpty()) score += 2;
        if (data.pages != null && !data.pages.isEmpty()) score += 3;
        if (data.doi != null && !data.doi.isEmpty()) score += 8;
        if (data.publisher != null && !data.publisher.isEmpty()) score += 2;
        return score;
    }

    /**
     * Merges data from another source into the best result.
     */
    private void mergeData(ReferenceData best, ReferenceData other) {
        if ((best.title == null || best.title.isEmpty()) && other.title != null) best.title = other.title;
        if ((best.authors == null || best.authors.isEmpty()) && other.authors != null) best.authors = other.authors;
        if ((best.year == null || best.year.isEmpty()) && other.year != null) best.year = other.year;
        if ((best.month == null || best.month.isEmpty()) && other.month != null) best.month = other.month;
        if ((best.containerTitle == null || best.containerTitle.isEmpty()) && other.containerTitle != null) best.containerTitle = other.containerTitle;
        if ((best.volume == null || best.volume.isEmpty()) && other.volume != null) best.volume = other.volume;
        if ((best.issue == null || best.issue.isEmpty()) && other.issue != null) best.issue = other.issue;
        if ((best.pages == null || best.pages.isEmpty()) && other.pages != null) best.pages = other.pages;
        if ((best.doi == null || best.doi.isEmpty()) && other.doi != null) best.doi = other.doi;
        if ((best.publisher == null || best.publisher.isEmpty()) && other.publisher != null) best.publisher = other.publisher;
        if ((best.isbn == null || best.isbn.isEmpty()) && other.isbn != null) best.isbn = other.isbn;
        if ((best.issn == null || best.issn.isEmpty()) && other.issn != null) best.issn = other.issn;
    }

    /**
     * Builds the verification result from reference data.
     */
    private VerificationResult buildResult(String bibEntry, ReferenceData data) {
        String entryType = "article";
        String key = "unknown";

        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(bibEntry);
        if (!parsed.entries().isEmpty()) {
            BibTeXParser.Entry e = parsed.entries().get(0);
            entryType = e.type();
            key = e.key();
        }

        if (data.type != null) {
            entryType = mapCrossRefType(data.type);
        }

        String corrected = buildCorrectedEntry(entryType, key, data, bibEntry);
        boolean changed = !normalizeForComparison(corrected).equals(normalizeForComparison(bibEntry));

        if (changed) {
            return new VerificationResult(bibEntry, corrected, VerificationStatus.CORRECTED,
                    "Reference corrected from " + data.source);
        } else {
            return new VerificationResult(bibEntry, bibEntry, VerificationStatus.VERIFIED,
                    "Reference verified via " + data.source);
        }
    }

    /**
     * Maps CrossRef/OpenAlex type to BibTeX entry type.
     */
    private String mapCrossRefType(String type) {
        return switch (type.toLowerCase()) {
            case "journal-article", "article" -> "article";
            case "book" -> "book";
            case "book-chapter", "chapter" -> "incollection";
            case "proceedings-article", "proceedings" -> "inproceedings";
            case "dissertation", "thesis" -> "phdthesis";
            case "report" -> "techreport";
            default -> "misc";
        };
    }

    /**
     * Builds a corrected BibTeX entry.
     * IMPORTANT: This method PRESERVES all original fields and only ADDS missing data.
     * It does NOT overwrite existing correct data to avoid corrupting well-formatted entries.
     */
    private String buildCorrectedEntry(String type, String key, ReferenceData data, String original) {
        // Parse all existing fields from the original entry
        java.util.Map<String, String> fields = parseAllFields(original);

        // Only add fields that are MISSING from the original
        // Do NOT overwrite existing fields - they might have correct LaTeX formatting

        if (!fields.containsKey("author") && data.authors != null && !data.authors.isEmpty()) {
            fields.put("author", data.authors);
        }

        if (!fields.containsKey("title") && data.title != null && !data.title.isEmpty()) {
            fields.put("title", data.title);
        }

        // Journal for articles, booktitle for proceedings
        String containerField = type.equals("inproceedings") || type.equals("incollection") ? "booktitle" : "journal";
        if (!fields.containsKey(containerField) && !fields.containsKey("journal") && !fields.containsKey("booktitle")) {
            if (data.containerTitle != null && !data.containerTitle.isEmpty()) {
                fields.put(containerField, data.containerTitle);
            }
        }

        if (!fields.containsKey("year") && data.year != null && !data.year.isEmpty()) {
            fields.put("year", data.year);
        }

        if (!fields.containsKey("month") && data.month != null && !data.month.isEmpty()) {
            fields.put("month", data.month);
        }

        if (!fields.containsKey("volume") && data.volume != null && !data.volume.isEmpty()) {
            fields.put("volume", data.volume);
        }

        if (!fields.containsKey("number") && data.issue != null && !data.issue.isEmpty()) {
            fields.put("number", data.issue);
        }

        if (!fields.containsKey("pages") && data.pages != null && !data.pages.isEmpty()) {
            fields.put("pages", data.pages);
        }

        if (!fields.containsKey("publisher") && data.publisher != null && !data.publisher.isEmpty()) {
            fields.put("publisher", data.publisher);
        }

        // DOI is very valuable - add if missing
        if (!fields.containsKey("doi") && data.doi != null && !data.doi.isEmpty()) {
            fields.put("doi", data.doi);
        }

        // ISBN for books
        if (!fields.containsKey("isbn") && data.isbn != null && !data.isbn.isEmpty()) {
            if (type.equals("book") || type.equals("incollection")) {
                fields.put("isbn", data.isbn);
            }
        }

        // ISSN for journals
        if (!fields.containsKey("issn") && data.issn != null && !data.issn.isEmpty()) {
            if (type.equals("article")) {
                fields.put("issn", data.issn);
            }
        }

        // Rebuild the entry preserving field order as much as possible
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(type).append("{").append(key).append(",\n");

        // Output fields in a standard order, but include ALL fields
        String[] standardOrder = {"author", "title", "journal", "booktitle", "year", "month",
                                   "volume", "number", "pages", "publisher", "organization",
                                   "doi", "isbn", "issn", "url", "note", "keywords", "abstract"};

        java.util.Set<String> outputted = new java.util.HashSet<>();

        // First output fields in standard order
        for (String fieldName : standardOrder) {
            if (fields.containsKey(fieldName)) {
                String value = fields.get(fieldName);
                sb.append("  ").append(fieldName).append(" = {").append(value).append("},\n");
                outputted.add(fieldName);
            }
        }

        // Then output any remaining fields that weren't in standard order
        for (java.util.Map.Entry<String, String> entry : fields.entrySet()) {
            if (!outputted.contains(entry.getKey())) {
                sb.append("  ").append(entry.getKey()).append(" = {").append(entry.getValue()).append("},\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses all fields from a BibTeX entry, preserving their exact values including LaTeX.
     */
    private java.util.Map<String, String> parseAllFields(String bibEntry) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();

        // Pattern to match field = {value} or field = "value" or field = value
        Pattern fieldPattern = Pattern.compile(
            "(\\w+)\\s*=\\s*(?:\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}|\"([^\"]*)\"|([\\w]+))",
            Pattern.CASE_INSENSITIVE
        );

        Matcher m = fieldPattern.matcher(bibEntry);
        while (m.find()) {
            String fieldName = m.group(1).toLowerCase();
            String value = m.group(2); // {braced value}
            if (value == null) value = m.group(3); // "quoted value"
            if (value == null) value = m.group(4); // bare value
            if (value != null) {
                fields.put(fieldName, value.trim());
            }
        }

        return fields;
    }

    private String cleanFieldValue(String value) {
        if (value == null) return null;
        return value.replaceAll("[{}]", "").trim();
    }

    private String normalizeForComparison(String s) {
        return s.toLowerCase().replaceAll("\\s+", " ").replaceAll("[{}]", "").trim();
    }

    private String extractJsonString(String json, String key) {
        Pattern arrayPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = arrayPattern.matcher(json);
        if (m.find()) return unescapeJson(m.group(1));

        Pattern simplePattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE);
        m = simplePattern.matcher(json);
        if (m.find()) return unescapeJson(m.group(1));

        Pattern numberPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
        m = numberPattern.matcher(json);
        if (m.find()) return m.group(1);

        return null;
    }

    private String extractNestedJsonString(String json, String parent, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(parent) + "\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return extractJsonString(m.group(1), key);
        }
        return null;
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private Optional<String> fetchUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "UniqueReferencesApp/2.0 (mailto:academic@example.com)")
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return Optional.of(response.body());
        } else if (response.statusCode() == 404 || response.statusCode() == 429) {
            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    /**
     * Data class to hold reference information from any source.
     * Package-private so tests in the same package can construct it.
     */
    static class ReferenceData {
        String source;
        String title;
        String doi;
        String type;
        String containerTitle;
        String volume;
        String issue;
        String pages;
        String publisher;
        String authors;
        String year;
        String month;
        String issn;
        String isbn;
        String url;
    }

    // --- Test-only hooks (package-private) ---

    java.util.Map<String, String> _testOnly_parseAllFields(String bibEntry) {
        return parseAllFields(bibEntry);
    }

    String _testOnly_buildCorrectedEntry(String type, String key, ReferenceData data, String original) {
        return buildCorrectedEntry(type, key, data, original);
    }
}

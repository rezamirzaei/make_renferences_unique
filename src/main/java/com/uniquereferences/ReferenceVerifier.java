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
    private static final String[] MONTH_FULL_NAMES = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

    private final VerificationMode mode;
    private final MonthNormalizer.MonthStyle monthStyle;

    private final HttpClient httpClient;

    public ReferenceVerifier() {
        this(VerificationMode.SAFE, MonthNormalizer.MonthStyle.KEEP_ORIGINAL);
    }

    public ReferenceVerifier(VerificationMode mode, MonthNormalizer.MonthStyle monthStyle) {
        this.mode = mode == null ? VerificationMode.SAFE : mode;
        this.monthStyle = monthStyle == null ? MonthNormalizer.MonthStyle.KEEP_ORIGINAL : monthStyle;
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

    public enum VerificationMode {
        SAFE,
        AGGRESSIVE_DOI_ONLY
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

            boolean doiSearch = doi != null && !doi.isEmpty();

            // Strategy 1: Search by DOI (most accurate)
            if (doiSearch) {
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
            if (results.isEmpty() && title != null && title.length() > 10) {
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

            boolean allowOverwrites = (mode == VerificationMode.AGGRESSIVE_DOI_ONLY) && doiSearch;

            // Build corrected entry
            return buildResult(bibEntry, best, allowOverwrites);

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
    private VerificationResult buildResult(String bibEntry, ReferenceData data, boolean allowOverwrites) {
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

        String corrected = buildCorrectedEntry(entryType, key, data, bibEntry, allowOverwrites);
        boolean changed = !normalizeForComparison(corrected).equals(normalizeForComparison(bibEntry));

        if (changed) {
            String msg = allowOverwrites ? "Reference corrected (aggressive) from " : "Reference corrected from ";
            return new VerificationResult(bibEntry, corrected, VerificationStatus.CORRECTED, msg + data.source);
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
     * In SAFE mode: preserves original fields and only adds missing.
     * In AGGRESSIVE_DOI_ONLY: may overwrite a small set of scalar fields.
     */
    private String buildCorrectedEntry(String type, String key, ReferenceData data, String original, boolean allowOverwrites) {
        java.util.Map<String, String> fields = parseAllFields(original);

        java.util.function.BiConsumer<String, String> putMaybe = (k, v) -> {
            if (v == null || v.isEmpty()) return;
            String kk = k.toLowerCase();
            boolean has = fields.containsKey(kk);
            if (!has) {
                fields.put(kk, v);
                return;
            }
            if (allowOverwrites && isOverwriteAllowedField(kk)) {
                fields.put(kk, v);
            }
        };

        putMaybe.accept("author", data.authors);
        putMaybe.accept("title", data.title);

        String containerField = type.equals("inproceedings") || type.equals("incollection") ? "booktitle" : "journal";
        if (!fields.containsKey(containerField) && !fields.containsKey("journal") && !fields.containsKey("booktitle")) {
            putMaybe.accept(containerField, data.containerTitle);
        } else if (allowOverwrites) {
            // overwrite only the matching container field if present
            if (fields.containsKey(containerField)) putMaybe.accept(containerField, data.containerTitle);
        }

        putMaybe.accept("year", data.year);

        // Handle month normalization
        String existingMonth = fields.get("month");
        if (existingMonth != null && !existingMonth.isEmpty() && monthStyle != MonthNormalizer.MonthStyle.KEEP_ORIGINAL) {
            // Normalize existing month to the selected style
            Integer mNum = MonthNormalizer.parseMonthNumber(existingMonth);
            if (mNum != null) {
                String formatted = MonthNormalizer.formatMonth(mNum, monthStyle, existingMonth);
                fields.put("month", formatted);
            }
        } else if (data.month != null && !data.month.isEmpty()) {
            // Add or update month from API data
            if (existingMonth == null) {
                Integer mNum = MonthNormalizer.parseMonthNumber(data.month);
                String formatted = MonthNormalizer.formatMonth(mNum, monthStyle, data.month);
                fields.put("month", formatted);
            } else if (allowOverwrites) {
                Integer mNum = MonthNormalizer.parseMonthNumber(data.month);
                String formatted = MonthNormalizer.formatMonth(mNum, monthStyle, data.month);
                fields.put("month", formatted);
            }
        }

        putMaybe.accept("volume", data.volume);
        putMaybe.accept("number", data.issue);
        putMaybe.accept("pages", data.pages);
        putMaybe.accept("publisher", data.publisher);
        putMaybe.accept("doi", data.doi);

        if (type.equals("book") || type.equals("incollection")) {
            putMaybe.accept("isbn", data.isbn);
        }
        if (type.equals("article")) {
            putMaybe.accept("issn", data.issn);
        }
        putMaybe.accept("url", data.url);

        return rebuildEntry(type, key, fields);
    }

    private boolean isOverwriteAllowedField(String fieldNameLower) {
        return switch (fieldNameLower) {
            case "doi", "url", "year", "month", "volume", "number", "pages", "journal", "booktitle" -> true;
            default -> false;
        };
    }

    private String rebuildEntry(String type, String key, java.util.Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(type).append("{").append(key).append(",\n");

        String[] standardOrder = {"author", "title", "journal", "booktitle", "year", "month",
                "volume", "number", "pages", "publisher", "organization",
                "doi", "isbn", "issn", "url", "note", "keywords", "abstract"};

        java.util.Set<String> outputted = new java.util.HashSet<>();

        for (String fieldName : standardOrder) {
            if (fields.containsKey(fieldName)) {
                String value = fields.get(fieldName);
                sb.append("  ").append(fieldName).append(" = {").append(value).append("},\n");
                outputted.add(fieldName);
            }
        }

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
     *
     * <p>This is a brace/quote-aware scanner (do NOT use regex for BibTeX fields).
     * It supports:
     * <ul>
     *   <li>field = { ... } with nested braces</li>
     *   <li>field = "..." with escapes</li>
     *   <li>field = bareValue</li>
     * </ul>
     */
    private java.util.Map<String, String> parseAllFields(String bibEntry) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        if (bibEntry == null || bibEntry.isBlank()) return fields;

        int n = bibEntry.length();

        // Start scanning after the first comma (after @type{key, or @type(key,)
        int start = bibEntry.indexOf(',');
        if (start < 0) return fields;
        int i = start + 1;

        while (i < n) {
            // skip whitespace and commas
            while (i < n) {
                char c = bibEntry.charAt(i);
                if (Character.isWhitespace(c) || c == ',') i++;
                else break;
            }
            if (i >= n) break;

            char c = bibEntry.charAt(i);
            if (c == '}' || c == ')') break;

            // parse field name
            int nameStart = i;
            while (i < n) {
                char cc = bibEntry.charAt(i);
                if (Character.isLetterOrDigit(cc) || cc == '_' || cc == '-') i++;
                else break;
            }
            if (i == nameStart) {
                i++;
                continue;
            }
            String fieldName = bibEntry.substring(nameStart, i).trim().toLowerCase();

            // skip whitespace
            while (i < n && Character.isWhitespace(bibEntry.charAt(i))) i++;
            if (i >= n || bibEntry.charAt(i) != '=') {
                // malformed; continue scanning
                continue;
            }
            i++; // '='
            while (i < n && Character.isWhitespace(bibEntry.charAt(i))) i++;
            if (i >= n) break;

            char open = bibEntry.charAt(i);
            String value;

            if (open == '{') {
                int depth = 1;
                int p = i + 1;
                boolean inQuotes = false;
                boolean escaped = false;
                while (p < n && depth > 0) {
                    char pc = bibEntry.charAt(p);
                    if (escaped) {
                        escaped = false;
                    } else if (pc == '\\') {
                        escaped = true;
                    } else if (pc == '"') {
                        inQuotes = !inQuotes;
                    } else if (!inQuotes) {
                        if (pc == '{') depth++;
                        else if (pc == '}') depth--;
                    }
                    p++;
                }
                if (depth != 0) {
                    // unbalanced; stop
                    break;
                }
                value = bibEntry.substring(i + 1, p - 1);
                i = p;
            } else if (open == '"') {
                int p = i + 1;
                boolean escaped = false;
                while (p < n) {
                    char pc = bibEntry.charAt(p);
                    if (escaped) {
                        escaped = false;
                    } else if (pc == '\\') {
                        escaped = true;
                    } else if (pc == '"') {
                        break;
                    }
                    p++;
                }
                if (p >= n) break;
                value = bibEntry.substring(i + 1, p);
                i = p + 1;
            } else {
                int p = i;
                while (p < n) {
                    char pc = bibEntry.charAt(p);
                    if (pc == ',' || pc == '}' || pc == ')') break;
                    p++;
                }
                value = bibEntry.substring(i, p);
                i = p;
            }

            value = value.trim();
            // Keep first occurrence to preserve original semantics
            fields.putIfAbsent(fieldName, value);
        }

        return fields;
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
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(parent) + "\\\"\\s*:\\s*\\{([^}]+)\\}");
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
     * Public so tests can construct it.
     */
    public static class ReferenceData {
        public String source;
        public String title;
        public String doi;
        public String type;
        public String containerTitle;
        public String volume;
        public String issue;
        public String pages;
        public String publisher;
        public String authors;
        public String year;
        public String month;
        public String issn;
        public String isbn;
        public String url;
    }

    // --- Test-only hooks (public for external testing) ---

    public java.util.Map<String, String> _testOnly_parseAllFields(String bibEntry) {
        return parseAllFields(bibEntry);
    }

    public String _testOnly_buildCorrectedEntry(String type, String key, ReferenceData data, String original) {
        return buildCorrectedEntry(type, key, data, original, false);
    }

    public String _testOnly_buildCorrectedEntryAggressive(String type, String key, ReferenceData data, String original) {
        return buildCorrectedEntry(type, key, data, original, true);
    }
}

package com.uniquereferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility for parsing BibTeX input and producing a de-duplicated set of entries.
 *
 * <p>Dedup policy: first key wins. This matches typical BibTeX behavior and keeps output stable.
 */
public final class BibTeXDeduplicator {

    private BibTeXDeduplicator() {
    }

    public static Result deduplicate(String input, boolean sortByKey, boolean smartDedup) {
        if (input == null || input.isBlank()) {
            return new Result(Map.of(), 0, 0, 0, 0);
        }

        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(input);

        Map<String, String> unique = sortByKey
            ? new TreeMap<>()
            : new LinkedHashMap<>();

        // For smart dedup: store normalized signatures of kept entries
        java.util.Set<String> signatures = new java.util.HashSet<>();

        int duplicates = 0;
        for (BibTeXParser.Entry e : parsed.entries()) {
            boolean isDuplicate = false;

            // 1. Check Key duplication (always active)
            if (unique.containsKey(e.key())) {
                isDuplicate = true;
            }
            // 2. Check Smart Deduplication (if enabled)
            else if (smartDedup) {
                String title = BibTeXParser.extractField(e.raw(), "title");
                if (title != null && !title.isBlank()) {
                    String normTitle = normalize(title);
                    if (normTitle.length() > 10) { // Only dedup if title is substantial
                        String year = BibTeXParser.extractField(e.raw(), "year");
                        String sig = normTitle + (year != null ? "|" + year.trim() : "");

                        if (signatures.contains(sig)) {
                            isDuplicate = true;
                        } else {
                            signatures.add(sig);
                        }
                    }
                }
            }

            if (isDuplicate) {
                duplicates++;
            } else {
                unique.put(e.key(), e.raw());
            }
        }

        return new Result(unique, parsed.entries().size(), unique.size(), duplicates, parsed.errors().size());
    }

    /**
     * Overload for backward compatibility/simplicity where smartDedup is false.
     */
    public static Result deduplicate(String input, boolean sortByKey) {
        return deduplicate(input, sortByKey, false);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public record Result(
        Map<String, String> uniqueEntries,
        int totalEntries,
        int uniqueCount,
        int duplicateCount,
        int parseErrorCount
    ) {}
}

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

    public enum DuplicateReason {
        KEY_DUPLICATE,
        TITLE_DUPLICATE,
        TITLE_YEAR_DUPLICATE
    }

    public record DuplicateRecord(
            String droppedKey,
            String keptKey,
            DuplicateReason reason
    ) {}

    public static Result deduplicate(String input, boolean sortByKey, boolean smartDedup) {
        if (input == null || input.isBlank()) {
            return new Result(Map.of(), 0, 0, 0, 0, java.util.List.of());
        }

        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(input);

        Map<String, String> unique = sortByKey
            ? new TreeMap<>()
            : new LinkedHashMap<>();

        java.util.List<DuplicateRecord> duplicateRecords = new java.util.ArrayList<>();

        // For smart dedup: store normalized signatures of kept entries
        java.util.Map<String, String> signatureToKeptKey = new java.util.HashMap<>();

        int duplicates = 0;
        for (BibTeXParser.Entry e : parsed.entries()) {
            String droppedKey = e.key();

            // 1) Key duplication (always)
            if (unique.containsKey(droppedKey)) {
                duplicates++;
                duplicateRecords.add(new DuplicateRecord(droppedKey, droppedKey, DuplicateReason.KEY_DUPLICATE));
                continue;
            }

            // 2) Smart dedupe (optional)
            String keptKey = null;
            DuplicateReason reason = null;

            if (smartDedup) {
                String title = BibTeXParser.extractField(e.raw(), "title");
                if (title != null && !title.isBlank()) {
                    String normTitle = normalize(title);
                    if (normTitle.length() >= 10) {
                        String year = BibTeXParser.extractField(e.raw(), "year");
                        String sigTitleOnly = normTitle;
                        String sigTitleYear = (year != null && !year.isBlank()) ? (normTitle + "|" + year.trim()) : null;

                        if (signatureToKeptKey.containsKey(sigTitleOnly)) {
                            keptKey = signatureToKeptKey.get(sigTitleOnly);
                            reason = DuplicateReason.TITLE_DUPLICATE;
                        } else if (sigTitleYear != null && signatureToKeptKey.containsKey(sigTitleYear)) {
                            keptKey = signatureToKeptKey.get(sigTitleYear);
                            reason = DuplicateReason.TITLE_YEAR_DUPLICATE;
                        }
                    }
                }
            }

            if (keptKey != null) {
                duplicates++;
                duplicateRecords.add(new DuplicateRecord(droppedKey, keptKey, reason));
                continue;
            }

            // Keep entry
            unique.put(droppedKey, e.raw());

            // Seed signatures for that kept entry
            if (smartDedup) {
                String title = BibTeXParser.extractField(e.raw(), "title");
                if (title != null && !title.isBlank()) {
                    String normTitle = normalize(title);
                    if (normTitle.length() >= 10) {
                        String year = BibTeXParser.extractField(e.raw(), "year");
                        signatureToKeptKey.putIfAbsent(normTitle, droppedKey);
                        if (year != null && !year.isBlank()) {
                            signatureToKeptKey.putIfAbsent(normTitle + "|" + year.trim(), droppedKey);
                        }
                    }
                }
            }
        }

        return new Result(unique, parsed.entries().size(), unique.size(), duplicates, parsed.errors().size(), duplicateRecords);
    }

    /**
     * Normalizes months in all entries according to the specified style.
     *
     * @param entries Map of key to raw BibTeX entry
     * @param monthStyle The style to normalize months to
     * @return A new map with normalized month fields
     */
    public static Map<String, String> normalizeMonths(Map<String, String> entries, MonthNormalizer.MonthStyle monthStyle) {
        if (monthStyle == null || monthStyle == MonthNormalizer.MonthStyle.KEEP_ORIGINAL) {
            return entries;
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String raw = entry.getValue();
            String normalizedEntry = normalizeMonthInEntry(raw, monthStyle);
            normalized.put(entry.getKey(), normalizedEntry);
        }
        return normalized;
    }

    /**
     * Normalizes the month field in a single BibTeX entry.
     */
    private static String normalizeMonthInEntry(String entry, MonthNormalizer.MonthStyle monthStyle) {
        String monthValue = BibTeXParser.extractField(entry, "month");
        if (monthValue == null || monthValue.isEmpty()) {
            return entry;
        }

        Integer monthNum = MonthNormalizer.parseMonthNumber(monthValue);
        if (monthNum == null) {
            return entry;
        }

        String newMonthValue = MonthNormalizer.formatMonth(monthNum, monthStyle, monthValue);
        if (newMonthValue.equals(monthValue)) {
            return entry; // No change needed
        }

        // Replace the month field value
        return replaceFieldValue(entry, "month", newMonthValue);
    }

    /**
     * Replaces a field value in a BibTeX entry, preserving structure.
     */
    private static String replaceFieldValue(String entry, String fieldName, String newValue) {
        // Pattern to match: month = {value} or month = "value" or month = value
        String lowerEntry = entry.toLowerCase();
        int fieldStart = lowerEntry.indexOf(fieldName + " ");
        if (fieldStart < 0) fieldStart = lowerEntry.indexOf(fieldName + "=");
        if (fieldStart < 0) fieldStart = lowerEntry.indexOf(fieldName + "\t");
        if (fieldStart < 0) return entry;

        // Find the = sign
        int eqPos = entry.indexOf('=', fieldStart);
        if (eqPos < 0) return entry;

        // Find the value start (skip whitespace after =)
        int valueStart = eqPos + 1;
        while (valueStart < entry.length() && Character.isWhitespace(entry.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= entry.length()) return entry;

        char openChar = entry.charAt(valueStart);
        int valueEnd;
        String replacement;

        if (openChar == '{') {
            // Find matching closing brace
            int depth = 1;
            valueEnd = valueStart + 1;
            while (valueEnd < entry.length() && depth > 0) {
                char c = entry.charAt(valueEnd);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                valueEnd++;
            }
            replacement = "{" + newValue + "}";
        } else if (openChar == '"') {
            valueEnd = entry.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return entry;
            valueEnd++; // Include the closing quote
            replacement = "{" + newValue + "}"; // Use braces for consistency
        } else {
            // Bare value - find comma or closing brace
            valueEnd = valueStart;
            while (valueEnd < entry.length()) {
                char c = entry.charAt(valueEnd);
                if (c == ',' || c == '}' || c == ')') break;
                valueEnd++;
            }
            replacement = "{" + newValue + "}";
        }

        return entry.substring(0, valueStart) + replacement + entry.substring(valueEnd);
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
        int parseErrorCount,
        java.util.List<DuplicateRecord> duplicates
    ) {}
}

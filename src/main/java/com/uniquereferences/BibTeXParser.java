package com.uniquereferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, resilient BibTeX entry parser.
 *
 * <p>Goal: reliably extract raw entry blocks and their keys from real-world .bib files.
 * We treat "entries" as things starting with '@' followed by an entry type, then a
 * brace-delimited or paren-delimited body.
 *
 * <p>We intentionally:
 * <ul>
 *   <li>Ignore braces/parens that occur inside quoted strings</li>
 *   <li>Handle escaped quotes (\")</li>
 *   <li>Handle nested braces</li>
 *   <li>Skip @comment/@preamble/@string (not reference entries)</li>
 * </ul>
 */
public final class BibTeXParser {

    private BibTeXParser() {
    }

    public static ParseResult parseEntries(String input) {
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (input == null || input.isEmpty()) {
            return new ParseResult(entries, errors);
        }

        int n = input.length();
        int i = 0;

        while (i < n) {
            int at = input.indexOf('@', i);
            if (at < 0) break;

            int typeStart = at + 1;
            while (typeStart < n && Character.isWhitespace(input.charAt(typeStart))) typeStart++;
            if (typeStart >= n) break;

            int typeEnd = typeStart;
            while (typeEnd < n) {
                char c = input.charAt(typeEnd);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    typeEnd++;
                } else {
                    break;
                }
            }

            if (typeEnd == typeStart) {
                // Not really an entry; skip this '@'
                i = at + 1;
                continue;
            }

            String type = input.substring(typeStart, typeEnd).trim().toLowerCase();

            int j = typeEnd;
            while (j < n && Character.isWhitespace(input.charAt(j))) j++;
            if (j >= n) break;

            char open = input.charAt(j);
            if (open != '{' && open != '(') {
                // Not an entry body
                i = j + 1;
                continue;
            }
            char close = (open == '{') ? '}' : ')';

            // Find key: read until first comma outside quotes/braces within the body header.
            // BibTeX allows whitespace.
            int k = j + 1;
            while (k < n && Character.isWhitespace(input.charAt(k))) k++;

            int keyStart = k;
            boolean inQuotes = false;
            boolean escaped = false;
            int nested = 0;

            while (k < n) {
                char c = input.charAt(k);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (!inQuotes) {
                    if (c == '{') nested++;
                    else if (c == '}') nested = Math.max(0, nested - 1);
                    else if (c == ',' && nested == 0) {
                        break;
                    } else if (c == close) {
                        // Body ended before comma → no key.
                        break;
                    }
                }
                k++;
            }

            String key = null;
            if (k > keyStart) {
                key = input.substring(keyStart, k).trim();
                if (key.isEmpty()) key = null;
            }

            // Now scan forward to find the matching closing brace/paren for the entry.
            int braceDepth = 1;
            int p = j + 1;
            inQuotes = false;
            escaped = false;

            while (p < n && braceDepth > 0) {
                char c = input.charAt(p);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (!inQuotes) {
                    if (c == open) braceDepth++;
                    else if (c == close) braceDepth--;
                }
                p++;
            }

            if (braceDepth != 0) {
                errors.add("Unclosed entry starting at index " + at + " (@" + type + ")");
                // Consume rest
                i = n;
                continue;
            }

            int entryEndExclusive = p; // p is already after the closing brace
            String raw = input.substring(at, entryEndExclusive).trim();

            // Skip non-reference constructs
            if (!type.equals("comment") && !type.equals("preamble") && !type.equals("string")) {
                if (key == null) {
                    errors.add("Entry without key at index " + at + " (@" + type + ")");
                } else {
                    entries.add(new Entry(type, key, raw));
                }
            }

            i = entryEndExclusive;
        }

        return new ParseResult(entries, errors);
    }

    public record Entry(String type, String key, String raw) {}

    public record ParseResult(List<Entry> entries, List<String> errors) {}

    /**
     * Extracts a specific field value from a raw BibTeX entry.
     *
     * <p>This is a small brace/quote-aware scanner (more reliable than regex for BibTeX).
     * It supports:
     * <ul>
     *   <li>field = { ... } with nested braces</li>
     *   <li>field = "..." with escaped quotes</li>
     *   <li>field = bareValue (until comma or end of entry)</li>
     * </ul>
     *
     * <p>Returns the inner value without outer delimiters, preserving LaTeX.
     */
    public static String extractField(String rawEntry, String fieldName) {
        if (rawEntry == null || fieldName == null) return null;
        String target = fieldName.trim().toLowerCase();
        if (target.isEmpty()) return null;

        int n = rawEntry.length();
        boolean inQuotes = false;
        boolean escaped = false;
        int braceDepth = 0;

        for (int i = 0; i < n; i++) {
            char c = rawEntry.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes) {
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth = Math.max(0, braceDepth - 1);
            }

            // We only consider field names at top-level inside the entry body.
            if (inQuotes || braceDepth > 1) {
                continue;
            }

            // Attempt to match field name at this position (word boundary-ish)
            if (!Character.isLetterOrDigit(c) && c != '_') {
                continue;
            }

            int nameStart = i;
            int nameEnd = i;
            while (nameEnd < n) {
                char cc = rawEntry.charAt(nameEnd);
                if (Character.isLetterOrDigit(cc) || cc == '_' || cc == '-') {
                    nameEnd++;
                } else {
                    break;
                }
            }

            if (nameEnd == nameStart) {
                continue;
            }

            String name = rawEntry.substring(nameStart, nameEnd).toLowerCase();
            if (!name.equals(target)) {
                i = nameEnd; // move forward
                continue;
            }

            int j = nameEnd;
            while (j < n && Character.isWhitespace(rawEntry.charAt(j))) j++;
            if (j >= n || rawEntry.charAt(j) != '=') {
                i = nameEnd;
                continue;
            }
            j++; // skip '='
            while (j < n && Character.isWhitespace(rawEntry.charAt(j))) j++;
            if (j >= n) return null;

            char open = rawEntry.charAt(j);
            if (open == '{') {
                // parse balanced braces
                int depth = 1;
                int p = j + 1;
                boolean q = false;
                boolean esc = false;
                while (p < n && depth > 0) {
                    char pc = rawEntry.charAt(p);
                    if (esc) {
                        esc = false;
                    } else if (pc == '\\') {
                        esc = true;
                    } else if (pc == '"') {
                        q = !q;
                    } else if (!q) {
                        if (pc == '{') depth++;
                        else if (pc == '}') depth--;
                    }
                    p++;
                }
                if (depth != 0) return null;
                String val = rawEntry.substring(j + 1, p - 1);
                return val.replaceAll("\\s+", " ").trim();
            }

            if (open == '"') {
                int p = j + 1;
                boolean esc = false;
                while (p < n) {
                    char pc = rawEntry.charAt(p);
                    if (esc) {
                        esc = false;
                    } else if (pc == '\\') {
                        esc = true;
                    } else if (pc == '"') {
                        break;
                    }
                    p++;
                }
                if (p >= n) return null;
                String val = rawEntry.substring(j + 1, p);
                return val.replaceAll("\\s+", " ").trim();
            }

            // bare value until comma or closing brace/paren at same nesting
            int p = j;
            while (p < n) {
                char pc = rawEntry.charAt(p);
                if (pc == ',' || pc == '}' || pc == ')') {
                    break;
                }
                p++;
            }
            String val = rawEntry.substring(j, p);
            return val.replaceAll("\\s+", " ").trim();
        }

        return null;
    }
}

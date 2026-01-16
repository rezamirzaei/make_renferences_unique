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
}

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

    public static Result deduplicate(String input, boolean sortByKey) {
        if (input == null || input.isBlank()) {
            return new Result(Map.of(), 0, 0, 0, 0);
        }

        BibTeXParser.ParseResult parsed = BibTeXParser.parseEntries(input);

        Map<String, String> unique = sortByKey
            ? new TreeMap<>()
            : new LinkedHashMap<>();

        int duplicates = 0;
        for (BibTeXParser.Entry e : parsed.entries()) {
            String existing = unique.putIfAbsent(e.key(), e.raw());
            if (existing != null) {
                duplicates++;
            }
        }

        return new Result(unique, parsed.entries().size(), unique.size(), duplicates, parsed.errors().size());
    }

    public record Result(
        Map<String, String> uniqueEntries,
        int totalEntries,
        int uniqueCount,
        int duplicateCount,
        int parseErrorCount
    ) {}
}

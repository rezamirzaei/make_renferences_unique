package com.uniquereferences;

import java.util.Locale;

/**
 * Month normalization helper.
 *
 * <p>Designed to be conservative:
 * - In SAFE mode, we generally keep existing month values unchanged.
 * - If we need to INSERT a missing month, we can format it using a chosen style.
 */
public final class MonthNormalizer {

    private MonthNormalizer() {}

    public enum MonthStyle {
        KEEP_ORIGINAL,
        ABBREV_DOT,   // Jan., Feb., ...
        FULL_NAME     // January, February, ...
    }

    private static final String[] FULL = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private static final String[] ABBREV_DOT = {
            "Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.",
            "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec."
    };

    /**
     * Parses a month string to 1..12 if possible.
     */
    public static Integer parseMonthNumber(String monthRaw) {
        if (monthRaw == null) return null;
        String m = monthRaw.trim();
        if (m.isEmpty()) return null;

        // numeric
        try {
            int v = Integer.parseInt(m.replaceAll("[^0-9]", ""));
            if (v >= 1 && v <= 12) return v;
        } catch (Exception ignored) {}

        String lower = m.toLowerCase(Locale.ROOT);
        lower = lower.replace("{", "").replace("}", "").trim();

        // common abbreviations / full names
        for (int i = 0; i < 12; i++) {
            String full = FULL[i].toLowerCase(Locale.ROOT);
            String abbr = ABBREV_DOT[i].toLowerCase(Locale.ROOT);
            if (lower.equals(full) || lower.startsWith(full.substring(0, 3))) return i + 1;
            if (lower.equals(abbr) || lower.equals(abbr.replace(".", ""))) return i + 1;
        }

        return null;
    }

    public static String formatMonth(Integer monthNumber, MonthStyle style, String original) {
        if (style == null) style = MonthStyle.KEEP_ORIGINAL;
        if (style == MonthStyle.KEEP_ORIGINAL) return original;
        if (monthNumber == null || monthNumber < 1 || monthNumber > 12) return original;

        return switch (style) {
            case FULL_NAME -> FULL[monthNumber - 1];
            case ABBREV_DOT -> ABBREV_DOT[monthNumber - 1];
            default -> original; // For KEEP_ORIGINAL or any future styles
        };
    }
}

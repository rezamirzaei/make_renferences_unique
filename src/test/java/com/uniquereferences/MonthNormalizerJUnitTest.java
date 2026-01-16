package com.uniquereferences;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonthNormalizerJUnitTest {

    @Test
    void parseMonthNumber_handlesAbbrevAndFullAndNumeric() {
        assertEquals(9, MonthNormalizer.parseMonthNumber("Sep."));
        assertEquals(9, MonthNormalizer.parseMonthNumber("September"));
        assertEquals(12, MonthNormalizer.parseMonthNumber("12"));
        assertEquals(2, MonthNormalizer.parseMonthNumber("{Feb.}"));
    }

    @Test
    void parseMonthNumber_handlesAllMonths() {
        // Full names
        assertEquals(1, MonthNormalizer.parseMonthNumber("January"));
        assertEquals(2, MonthNormalizer.parseMonthNumber("February"));
        assertEquals(3, MonthNormalizer.parseMonthNumber("March"));
        assertEquals(4, MonthNormalizer.parseMonthNumber("April"));
        assertEquals(5, MonthNormalizer.parseMonthNumber("May"));
        assertEquals(6, MonthNormalizer.parseMonthNumber("June"));
        assertEquals(7, MonthNormalizer.parseMonthNumber("July"));
        assertEquals(8, MonthNormalizer.parseMonthNumber("August"));
        assertEquals(9, MonthNormalizer.parseMonthNumber("September"));
        assertEquals(10, MonthNormalizer.parseMonthNumber("October"));
        assertEquals(11, MonthNormalizer.parseMonthNumber("November"));
        assertEquals(12, MonthNormalizer.parseMonthNumber("December"));

        // Abbreviations with dots
        assertEquals(1, MonthNormalizer.parseMonthNumber("Jan."));
        assertEquals(2, MonthNormalizer.parseMonthNumber("Feb."));
        assertEquals(3, MonthNormalizer.parseMonthNumber("Mar."));
        assertEquals(4, MonthNormalizer.parseMonthNumber("Apr."));
        assertEquals(5, MonthNormalizer.parseMonthNumber("May"));
        assertEquals(6, MonthNormalizer.parseMonthNumber("Jun."));
        assertEquals(7, MonthNormalizer.parseMonthNumber("Jul."));
        assertEquals(8, MonthNormalizer.parseMonthNumber("Aug."));
        assertEquals(9, MonthNormalizer.parseMonthNumber("Sep."));
        assertEquals(10, MonthNormalizer.parseMonthNumber("Oct."));
        assertEquals(11, MonthNormalizer.parseMonthNumber("Nov."));
        assertEquals(12, MonthNormalizer.parseMonthNumber("Dec."));

        // Case insensitive
        assertEquals(7, MonthNormalizer.parseMonthNumber("july"));
        assertEquals(7, MonthNormalizer.parseMonthNumber("JULY"));
        assertEquals(12, MonthNormalizer.parseMonthNumber("dec."));
    }

    @Test
    void parseMonthNumber_handlesNumeric() {
        assertEquals(1, MonthNormalizer.parseMonthNumber("1"));
        assertEquals(12, MonthNormalizer.parseMonthNumber("12"));
        assertEquals(6, MonthNormalizer.parseMonthNumber("06"));
    }

    @Test
    void parseMonthNumber_handlesNullAndEmpty() {
        assertNull(MonthNormalizer.parseMonthNumber(null));
        assertNull(MonthNormalizer.parseMonthNumber(""));
        assertNull(MonthNormalizer.parseMonthNumber("   "));
    }

    @Test
    void formatMonth_respectsStyle() {
        assertEquals("September", MonthNormalizer.formatMonth(9, MonthNormalizer.MonthStyle.FULL_NAME, "Sep."));
        assertEquals("Sep.", MonthNormalizer.formatMonth(9, MonthNormalizer.MonthStyle.ABBREV_DOT, "September"));
        assertEquals("Sep.", MonthNormalizer.formatMonth(9, MonthNormalizer.MonthStyle.KEEP_ORIGINAL, "Sep."));
    }

    @Test
    void formatMonth_allMonthsFullName() {
        assertEquals("January", MonthNormalizer.formatMonth(1, MonthNormalizer.MonthStyle.FULL_NAME, "Jan."));
        assertEquals("February", MonthNormalizer.formatMonth(2, MonthNormalizer.MonthStyle.FULL_NAME, "Feb."));
        assertEquals("March", MonthNormalizer.formatMonth(3, MonthNormalizer.MonthStyle.FULL_NAME, "Mar."));
        assertEquals("April", MonthNormalizer.formatMonth(4, MonthNormalizer.MonthStyle.FULL_NAME, "Apr."));
        assertEquals("May", MonthNormalizer.formatMonth(5, MonthNormalizer.MonthStyle.FULL_NAME, "May"));
        assertEquals("June", MonthNormalizer.formatMonth(6, MonthNormalizer.MonthStyle.FULL_NAME, "Jun."));
        assertEquals("July", MonthNormalizer.formatMonth(7, MonthNormalizer.MonthStyle.FULL_NAME, "Jul."));
        assertEquals("August", MonthNormalizer.formatMonth(8, MonthNormalizer.MonthStyle.FULL_NAME, "Aug."));
        assertEquals("September", MonthNormalizer.formatMonth(9, MonthNormalizer.MonthStyle.FULL_NAME, "Sep."));
        assertEquals("October", MonthNormalizer.formatMonth(10, MonthNormalizer.MonthStyle.FULL_NAME, "Oct."));
        assertEquals("November", MonthNormalizer.formatMonth(11, MonthNormalizer.MonthStyle.FULL_NAME, "Nov."));
        assertEquals("December", MonthNormalizer.formatMonth(12, MonthNormalizer.MonthStyle.FULL_NAME, "Dec."));
    }

    @Test
    void formatMonth_allMonthsAbbrevDot() {
        assertEquals("Jan.", MonthNormalizer.formatMonth(1, MonthNormalizer.MonthStyle.ABBREV_DOT, "January"));
        assertEquals("Feb.", MonthNormalizer.formatMonth(2, MonthNormalizer.MonthStyle.ABBREV_DOT, "February"));
        assertEquals("Mar.", MonthNormalizer.formatMonth(3, MonthNormalizer.MonthStyle.ABBREV_DOT, "March"));
        assertEquals("Apr.", MonthNormalizer.formatMonth(4, MonthNormalizer.MonthStyle.ABBREV_DOT, "April"));
        assertEquals("May", MonthNormalizer.formatMonth(5, MonthNormalizer.MonthStyle.ABBREV_DOT, "May"));
        assertEquals("Jun.", MonthNormalizer.formatMonth(6, MonthNormalizer.MonthStyle.ABBREV_DOT, "June"));
        assertEquals("Jul.", MonthNormalizer.formatMonth(7, MonthNormalizer.MonthStyle.ABBREV_DOT, "July"));
        assertEquals("Aug.", MonthNormalizer.formatMonth(8, MonthNormalizer.MonthStyle.ABBREV_DOT, "August"));
        assertEquals("Sep.", MonthNormalizer.formatMonth(9, MonthNormalizer.MonthStyle.ABBREV_DOT, "September"));
        assertEquals("Oct.", MonthNormalizer.formatMonth(10, MonthNormalizer.MonthStyle.ABBREV_DOT, "October"));
        assertEquals("Nov.", MonthNormalizer.formatMonth(11, MonthNormalizer.MonthStyle.ABBREV_DOT, "November"));
        assertEquals("Dec.", MonthNormalizer.formatMonth(12, MonthNormalizer.MonthStyle.ABBREV_DOT, "December"));
    }

    @Test
    void formatMonth_handlesInvalidInput() {
        // Null month number returns original
        assertEquals("Sep.", MonthNormalizer.formatMonth(null, MonthNormalizer.MonthStyle.FULL_NAME, "Sep."));

        // Out of range month returns original
        assertEquals("Sep.", MonthNormalizer.formatMonth(0, MonthNormalizer.MonthStyle.FULL_NAME, "Sep."));
        assertEquals("Sep.", MonthNormalizer.formatMonth(13, MonthNormalizer.MonthStyle.FULL_NAME, "Sep."));
    }
}

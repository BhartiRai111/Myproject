package com.storehub.util;

import java.time.LocalDate;

/**
 * Indian financial year convention (1 April → 31 March), computed purely
 * from a date — there is no persisted FinancialYear/Company/Branch entity
 * anywhere in this app (confirmed absent in Phase 3/4 inspection), so this
 * is a report-level convention only, never a stored setting.
 */
public final class FinancialYearUtil {

    private FinancialYearUtil() {
    }

    /** Start (1 April) of the financial year containing {@code date}. */
    public static LocalDate startOf(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(startYear, 4, 1);
    }

    /** End (31 March) of the financial year containing {@code date}. */
    public static LocalDate endOf(LocalDate date) {
        return startOf(date).plusYears(1).minusDays(1);
    }

    /** Label such as "2026-27" for the financial year containing {@code date}. */
    public static String label(LocalDate date) {
        LocalDate start = startOf(date);
        return start.getYear() + "-" + String.format("%02d", (start.getYear() + 1) % 100);
    }
}

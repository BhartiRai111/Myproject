package com.storehub.util;

import java.time.LocalDate;

/**
 * Indian financial year convention (1 April → 31 March), computed purely
 * from a date. Used to auto-seed and validate {@link com.storehub.entity.FinancialYear}
 * rows (Phase 5) — this class only knows the calendar rule, never whether a
 * year is OPEN/CLOSED/current, which is what the persisted entity and
 * {@link com.storehub.service.FinancialYearService} are for.
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

    /** Short code such as "26-27", as used in voucher numbers (SALE/26-27/000001). */
    public static String shortCode(LocalDate date) {
        LocalDate start = startOf(date);
        return String.format("%02d", start.getYear() % 100) + "-" + String.format("%02d", (start.getYear() + 1) % 100);
    }
}

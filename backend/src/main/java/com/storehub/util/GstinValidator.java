package com.storehub.util;

import java.util.regex.Pattern;

/**
 * The single reusable GSTIN validator for the whole app (none existed
 * before Phase 3, front or back end). A 15-character GSTIN is:
 * 2-digit state code + 10-character PAN + 1-digit entity code + 'Z' +
 * 1 checksum character. Used to classify a Sale/Purchase party as B2B
 * (valid GSTIN on record) vs B2C, and to derive the GST place-of-supply
 * state code as the GSTIN's first 2 digits — no new master-data table
 * needed since the state code is embedded in the GSTIN itself.
 */
public final class GstinValidator {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private GstinValidator() {
    }

    public static boolean isValid(String gstin) {
        if (gstin == null) {
            return false;
        }
        String trimmed = gstin.trim().toUpperCase();
        return GSTIN_PATTERN.matcher(trimmed).matches();
    }

    /** The 2-digit GST state code embedded in a valid GSTIN, or null if the GSTIN is not valid. */
    public static String extractStateCode(String gstin) {
        if (!isValid(gstin)) {
            return null;
        }
        return gstin.trim().toUpperCase().substring(0, 2);
    }
}

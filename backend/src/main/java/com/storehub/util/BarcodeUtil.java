package com.storehub.util;

import com.storehub.entity.BarcodeType;

import java.util.regex.Pattern;

/**
 * Barcode normalization, classification, and check-digit validation
 * (Barcode Management spec sections 5/9/10/11). A barcode is always a plain
 * String — trimmed only, never uppercased (unlike SKU) and never parsed as a
 * number, so leading zeroes and Code128's case-sensitive characters survive
 * exactly as scanned.
 */
public final class BarcodeUtil {

    private BarcodeUtil() {
    }

    /** Same safe character set as SKU (also closes off CSV formula-injection triggers such as a leading '='). */
    private static final Pattern SAFE_CHARACTERS = Pattern.compile("^[A-Za-z0-9\\-_/]+$");
    private static final Pattern EAN13_DIGITS = Pattern.compile("^\\d{13}$");
    private static final Pattern EAN8_DIGITS = Pattern.compile("^\\d{8}$");
    private static final Pattern UPC_DIGITS = Pattern.compile("^\\d{12}$");
    private static final String INTERNAL_PREFIX = "INT-";

    /** Trims whitespace only — no case change, no numeric parsing. */
    public static String normalize(String barcode) {
        return barcode == null ? null : barcode.trim();
    }

    public static boolean isValidCharacters(String barcode) {
        return barcode != null && SAFE_CHARACTERS.matcher(barcode).matches();
    }

    /**
     * Auto-detects the barcode's type purely from its shape — the user never
     * picks a type. Detection order matters: INTERNAL (our own prefix) is
     * checked before the digit-length patterns since it is never numeric-only.
     */
    public static BarcodeType detectType(String barcode) {
        if (barcode == null) {
            return null;
        }
        if (barcode.startsWith(INTERNAL_PREFIX)) {
            return BarcodeType.INTERNAL;
        }
        if (EAN13_DIGITS.matcher(barcode).matches()) {
            return BarcodeType.EAN13;
        }
        if (UPC_DIGITS.matcher(barcode).matches()) {
            return BarcodeType.UPC;
        }
        if (EAN8_DIGITS.matcher(barcode).matches()) {
            return BarcodeType.EAN8;
        }
        return BarcodeType.OTHER;
    }

    /**
     * Standard EAN-13 checksum: sum digits at odd positions (1-indexed) as-is,
     * digits at even positions x3, take mod 10, and the check digit (last
     * digit) must equal (10 - mod) % 10. Only called when the barcode is
     * already known to be 13 digits (spec section 11 — never forced on a
     * barcode that isn't claiming to be EAN-13 in the first place).
     */
    public static boolean isValidEan13Checksum(String ean13) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = ean13.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int expectedCheckDigit = (10 - (sum % 10)) % 10;
        int actualCheckDigit = ean13.charAt(12) - '0';
        return expectedCheckDigit == actualCheckDigit;
    }
}

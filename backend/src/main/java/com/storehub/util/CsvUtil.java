package com.storehub.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC4180-style CSV read/write helper. No external dependency —
 * Phase 6 import/export needs only flat, quoted-when-necessary fields.
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    private static final String FORMULA_TRIGGER_CHARS = "=+-@\t\r";

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        // Neutralize spreadsheet formula injection: a cell that Excel/LibreOffice/Sheets would
        // otherwise interpret as a formula (=, +, -, @, or leading tab/CR) gets a literal-text
        // prefix before the normal RFC4180 quoting below. Guards against a product name/category
        // planted by one user (e.g. STORE_MANAGER) executing in another user's spreadsheet on export.
        if (!s.isEmpty() && FORMULA_TRIGGER_CHARS.indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    /** Parses a single CSV line into fields, honoring quoted fields with embedded commas/quotes. */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }
}

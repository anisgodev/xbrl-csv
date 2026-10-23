package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.List;

/** CSV writing and reading per RFC 4180 (comma separator, double quotes, CRLF line endings). */
final class Csv {

    static final String LINE_ENDING = "\r\n";

    private Csv() {
    }

    static String write(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        writeRow(sb, headers);
        for (List<String> row : rows) {
            writeRow(sb, row);
        }
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values.get(i)));
        }
        sb.append(LINE_ENDING);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needsQuoting ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    /** Reads a complete CSV content; the first row is the header. Trailing empty lines are ignored. */
    static List<List<String>> read(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        if (content.startsWith("﻿")) {
            i = 1;
        }
        while (i < content.length()) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' || c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(c);
            }
            i++;
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Invalid CSV: unclosed quote");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        rows.removeIf(r -> r.size() == 1 && r.get(0).isEmpty());
        return rows;
    }
}

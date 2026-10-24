package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV writing and reading per RFC 4180 (comma separator, double quotes, CRLF line endings).
 *
 * <p>Writing is strict. Reading is lenient, to accept files from other tools: LF or CR line endings, a leading BOM,
 * a quote inside an unquoted value ({@code ab"c"} reads as {@code abc}) and text after a closing quote
 * ({@code "a"b} reads as {@code ab}); only an unclosed quote is an error.
 */
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
        if (values.size() == 1 && (values.get(0) == null || values.get(0).isEmpty())) {
            // A lone empty value is quoted, otherwise the row would be a blank line, which reading ignores.
            sb.append("\"\"").append(LINE_ENDING);
            return;
        }
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

    /**
     * Reads a complete CSV content; the first row is the header. Blank lines are ignored; a row made of a single
     * quoted empty value ({@code ""}) is kept.
     */
    static List<List<String>> read(String content) {
        return read(content, Integer.MAX_VALUE);
    }

    /** As {@link #read(String)}, refusing content with more than {@code maxValues} values in all. */
    static List<List<String>> read(String content, int maxValues) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowQuoted = false;
        int values = 0;
        int i = 0;
        if (content.startsWith("\uFEFF")) {
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
                rowQuoted = true;
            } else if (c == ',') {
                values = addValue(row, field, values, maxValues);
            } else if (c == '\r' || c == '\n') {
                values = addValue(row, field, values, maxValues);
                addUnlessBlank(rows, row, rowQuoted);
                row = new ArrayList<>();
                rowQuoted = false;
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
        if (field.length() > 0 || !row.isEmpty() || rowQuoted) {
            addValue(row, field, values, maxValues);
            addUnlessBlank(rows, row, rowQuoted);
        }
        return rows;
    }

    private static int addValue(List<String> row, StringBuilder field, int values, int maxValues) {
        if (values >= maxValues) {
            throw new TooManyValuesException(maxValues);
        }
        // Empty values share one instance: a file made of commas would otherwise cost one String per comma.
        row.add(field.isEmpty() ? "" : field.toString());
        field.setLength(0);
        return values + 1;
    }

    private static void addUnlessBlank(List<List<String>> rows, List<String> row, boolean quoted) {
        if (quoted || row.size() > 1 || !row.get(0).isEmpty()) {
            rows.add(row);
        }
    }

    /** The content holds more values than the cap given to {@link #read(String, int)}. */
    static final class TooManyValuesException extends IllegalArgumentException {
        TooManyValuesException(int maxValues) {
            super("Invalid CSV: too many values (more than " + maxValues + ")");
        }
    }
}

package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A report table (for example {@code B_01.01}): column codes and rows of values.
 * A missing value is an empty string.
 */
public record Table(String code, List<String> columns, List<List<String>> rows) {

    public Table {
        Objects.requireNonNull(code, "code");
        columns = List.copyOf(columns);
        rows = rows.stream().map(r -> r.stream().map(v -> v == null ? "" : v).toList()).toList();
    }

    public static Builder builder(String code, String... columns) {
        return new Builder(code, List.of(columns));
    }

    public static Builder builder(String code, List<String> columns) {
        return new Builder(code, columns);
    }

    /** Name of the CSV file in the package: the code in lower case, for example {@code b_01.01.csv}. */
    public String fileName() {
        return fileName(code);
    }

    public static String fileName(String code) {
        return code.toLowerCase(Locale.ROOT) + ".csv";
    }

    /** Table code derived from a file name, for example {@code b_01.01.csv} gives {@code B_01.01}. */
    public static String codeFromFileName(String fileName) {
        String base = fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return base.toUpperCase(Locale.ROOT);
    }

    public int columnIndex(String column) {
        return columns.indexOf(column);
    }

    public static final class Builder {
        private final String code;
        private final List<String> columns;
        private final List<List<String>> rows = new ArrayList<>();

        private Builder(String code, List<String> columns) {
            this.code = code;
            this.columns = List.copyOf(columns);
        }

        public Builder row(String... values) {
            rows.add(new ArrayList<>(Arrays.asList(values)));
            return this;
        }

        public Builder row(List<String> values) {
            rows.add(new ArrayList<>(values));
            return this;
        }

        public Table build() {
            return new Table(code, columns, rows);
        }
    }
}

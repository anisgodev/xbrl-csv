package tech.anisgodhbane.xbrlcsv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads back an xBRL-CSV report package archive, for example a client's filing from the previous year.
 *
 * <p>Guards against malicious archives: uncompressed size (200 MB), number of entries (500) and number of CSV values
 * are capped, paths climbing out of the tree, duplicate entries and several reports in one archive are rejected.
 *
 * <p>The package is read whole into memory, and the parsed values dominate: count about 50 bytes of heap per value, so
 * up to about 250 MB at the default cap of {@value #DEFAULT_MAX_VALUES} values. A small archive can reach that cap;
 * {@link #XbrlCsvReader(int)} sets a lower or higher one.
 *
 * <p>{@code parameters.csv} must have the header {@code name,value} and {@code FilingIndicators.csv} the header
 * {@code templateID,reported} (case ignored), each row with exactly two values. Tables come back in the order of the
 * filing indicators, then the undeclared ones by file name; a table takes the code spelled by its filing indicator,
 * else its upper-cased file name. A parameter named twice and a {@code reports/} folder anywhere but directly in the
 * root folder are rejected. {@link #read(InputStream)} leaves the caller's stream open.
 */
public final class XbrlCsvReader {

    private static final long MAX_UNCOMPRESSED_SIZE = 200L * 1024 * 1024;
    private static final int MAX_ENTRIES = 500;
    private static final String PARAMETERS = "parameters.csv";
    private static final String FILING_INDICATORS = "FilingIndicators.csv";

    /** Default cap on the CSV values of a package, all files together; a very large register holds 3 to 5 million. */
    public static final int DEFAULT_MAX_VALUES = 5_000_000;

    private final ObjectMapper json = new ObjectMapper();
    private final int maxValues;

    /** A reader capped at {@value #DEFAULT_MAX_VALUES} CSV values per package. */
    public XbrlCsvReader() {
        this(DEFAULT_MAX_VALUES);
    }

    /** A reader capped at {@code maxValues} CSV values per package, all files together; at least 1. */
    public XbrlCsvReader(int maxValues) {
        if (maxValues < 1) {
            throw new IllegalArgumentException("maxValues must be at least 1: " + maxValues);
        }
        this.maxValues = maxValues;
    }

    public ReportPackage read(byte[] archive) {
        return read(new ByteArrayInputStream(archive));
    }

    public ReportPackage read(InputStream archive) {
        Map<String, String> files = unzip(archive);

        List<String> reportPaths = files.keySet().stream()
                .filter(p -> p.endsWith("/reports/report.json"))
                .toList();
        if (reportPaths.isEmpty()) {
            throw new IllegalArgumentException("Invalid package: reports/report.json missing");
        }
        if (reportPaths.size() > 1) {
            throw new IllegalArgumentException("Invalid package: several reports/report.json " + reportPaths);
        }
        String reportPath = reportPaths.get(0);
        String prefix = reportPath.substring(0, reportPath.length() - "report.json".length());
        String name = reportPath.substring(0, reportPath.indexOf('/'));
        if (!prefix.equals(name + "/reports/")) {
            throw new IllegalArgumentException("Invalid package: reports/ must sit directly in the root folder: "
                    + reportPath);
        }

        ValueBudget budget = new ValueBudget(maxValues);
        String entryPoint = readEntryPoint(files.get(reportPath));
        Parameters parameters = readParameters(files.get(prefix + PARAMETERS), budget);
        List<FilingIndicator> filingIndicators = readFilingIndicators(files.get(prefix + FILING_INDICATORS), budget);

        // The writer lower-cases file names: give each table the code its filing indicator spells.
        Map<String, String> declaredCodes = new HashMap<>();
        for (FilingIndicator indicator : filingIndicators) {
            declaredCodes.putIfAbsent(Table.fileName(indicator.templateId()), indicator.templateId());
        }
        List<Table> tables = new ArrayList<>();
        new TreeMap<>(files).forEach((path, content) -> {
            if (!path.startsWith(prefix) || !path.endsWith(".csv")) {
                return;
            }
            String file = path.substring(prefix.length());
            if (file.contains("/") || file.equals(PARAMETERS) || file.equals(FILING_INDICATORS)) {
                return;
            }
            List<List<String>> rows = budget.read(content);
            if (rows.isEmpty()) {
                return;
            }
            String code = declaredCodes.getOrDefault(file.toLowerCase(Locale.ROOT), Table.codeFromFileName(file));
            tables.add(new Table(code, rows.get(0), rows.subList(1, rows.size())));
        });
        Map<String, Integer> declarationOrder = new HashMap<>();
        for (FilingIndicator indicator : filingIndicators) {
            declarationOrder.putIfAbsent(indicator.templateId(), declarationOrder.size());
        }
        // Stable sort: undeclared tables stay in file name order, after the declared ones.
        tables.sort(Comparator.comparingInt(t -> declarationOrder.getOrDefault(t.code(), Integer.MAX_VALUE)));

        return new ReportPackage(name, entryPoint, parameters, filingIndicators, tables);
    }

    private String readEntryPoint(String reportJson) {
        try {
            JsonNode extendsNode = json.readTree(reportJson).path("documentInfo").path("extends");
            if (!extendsNode.isArray() || extendsNode.isEmpty()) {
                throw new IllegalArgumentException("Invalid package: documentInfo.extends missing from report.json");
            }
            return extendsNode.get(0).asText();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid package: report.json unreadable", e);
        }
    }

    private static Parameters readParameters(String content, ValueBudget budget) {
        Map<String, String> values = new LinkedHashMap<>();
        if (content != null) {
            for (List<String> row : readTwoColumns(content, PARAMETERS, "name", "value", budget)) {
                if (values.put(row.get(0), row.get(1)) != null) {
                    throw new IllegalArgumentException("Invalid package: parameter " + row.get(0) + " appears twice in "
                            + PARAMETERS);
                }
            }
        }
        return new Parameters(values);
    }

    private static List<FilingIndicator> readFilingIndicators(String content, ValueBudget budget) {
        List<FilingIndicator> filingIndicators = new ArrayList<>();
        if (content != null) {
            for (List<String> row : readTwoColumns(content, FILING_INDICATORS, "templateID", "reported", budget)) {
                filingIndicators.add(new FilingIndicator(row.get(0), parseReported(row.get(1).strip())));
            }
        }
        return filingIndicators;
    }

    /** The data rows of a two-column file, after checking its header and the width of every row. */
    private static List<List<String>> readTwoColumns(String content, String file, String first, String second,
                                                     ValueBudget budget) {
        List<List<String>> rows = budget.read(content);
        if (rows.isEmpty() || rows.get(0).size() != 2
                || !rows.get(0).get(0).strip().equalsIgnoreCase(first)
                || !rows.get(0).get(1).strip().equalsIgnoreCase(second)) {
            throw new IllegalArgumentException("Invalid package: " + file + " must start with the header " + first
                    + "," + second);
        }
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).size() != 2) {
                throw new IllegalArgumentException("Invalid package: " + file + " row " + i + " has "
                        + rows.get(i).size() + " values instead of 2");
            }
        }
        return rows.subList(1, rows.size());
    }

    private static boolean parseReported(String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid package: reported must be true or false in FilingIndicators.csv: "
                + value);
    }

    private static Map<String, String> unzip(InputStream archive) {
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new NonClosingInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Invalid package: too many entries");
                }
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.contains("../")) {
                    throw new IllegalArgumentException("Invalid package: forbidden path " + path);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_SIZE) {
                        throw new IllegalArgumentException("Invalid package: uncompressed size too large");
                    }
                    content.write(buffer, 0, read);
                }
                if (files.put(path, content.toString(StandardCharsets.UTF_8)) != null) {
                    throw new IllegalArgumentException("Invalid package: duplicate entry " + path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the xBRL-CSV package", e);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Invalid package: empty or non-ZIP archive");
        }
        return files;
    }

    /** What remains of the value cap while one package is read. */
    private static final class ValueBudget {
        private final int maxValues;
        private int remaining;

        ValueBudget(int maxValues) {
            this.maxValues = maxValues;
            remaining = maxValues;
        }

        List<List<String>> read(String content) {
            List<List<String>> rows;
            try {
                rows = Csv.read(content, remaining);
            } catch (Csv.TooManyValuesException e) {
                throw new IllegalArgumentException("Invalid package: too many CSV values (more than " + maxValues
                        + " in all)", e);
            }
            for (List<String> row : rows) {
                remaining -= row.size();
            }
            return rows;
        }
    }

    /** Closing the archive must not close the caller's stream. */
    private static final class NonClosingInputStream extends FilterInputStream {

        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }
}

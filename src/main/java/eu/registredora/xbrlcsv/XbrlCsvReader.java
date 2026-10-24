package eu.registredora.xbrlcsv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads back an xBRL-CSV report package archive, for example a client's filing from the previous year.
 *
 * <p>Guards against malicious archives: uncompressed size and number of entries are capped, paths climbing out of
 * the tree, duplicate entries and several reports in one archive are rejected.
 */
public final class XbrlCsvReader {

    private static final long MAX_UNCOMPRESSED_SIZE = 200L * 1024 * 1024;
    private static final int MAX_ENTRIES = 500;

    private final ObjectMapper json = new ObjectMapper();

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

        String entryPoint = readEntryPoint(files.get(reportPath));
        Parameters parameters = readParameters(files.get(prefix + "parameters.csv"));
        List<FilingIndicator> filingIndicators = readFilingIndicators(files.get(prefix + "FilingIndicators.csv"));

        List<Table> tables = new ArrayList<>();
        new TreeMap<>(files).forEach((path, content) -> {
            if (!path.startsWith(prefix) || !path.endsWith(".csv")) {
                return;
            }
            String file = path.substring(prefix.length());
            if (file.contains("/") || file.equals("parameters.csv") || file.equals("FilingIndicators.csv")) {
                return;
            }
            List<List<String>> rows = Csv.read(content);
            if (rows.isEmpty()) {
                return;
            }
            tables.add(new Table(Table.codeFromFileName(file), rows.get(0), rows.subList(1, rows.size())));
        });

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

    private static Parameters readParameters(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        if (content != null) {
            List<List<String>> rows = Csv.read(content);
            for (List<String> row : rows.subList(Math.min(1, rows.size()), rows.size())) {
                if (row.size() >= 2) {
                    values.put(row.get(0), row.get(1));
                }
            }
        }
        return new Parameters(values);
    }

    private static List<FilingIndicator> readFilingIndicators(String content) {
        List<FilingIndicator> filingIndicators = new ArrayList<>();
        if (content != null) {
            List<List<String>> rows = Csv.read(content);
            for (List<String> row : rows.subList(Math.min(1, rows.size()), rows.size())) {
                if (row.size() >= 2) {
                    filingIndicators.add(new FilingIndicator(row.get(0), parseReported(row.get(1).strip())));
                }
            }
        }
        return filingIndicators;
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
        try (ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
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
}

package tech.anisgodhbane.xbrlcsv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a {@link ReportPackage} as an xBRL-CSV report package ZIP archive:
 *
 * <pre>
 * {name}/META-INF/reportPackage.json
 * {name}/reports/report.json
 * {name}/reports/parameters.csv
 * {name}/reports/FilingIndicators.csv
 * {name}/reports/b_01.01.csv ...
 * </pre>
 *
 * <p>Files are UTF-8 without BOM. Entry order and timestamps are fixed: the same package always produces the same
 * bytes, hence the same hash, whatever the machine or its time zone.
 *
 * <p>Refuses, with an {@link IllegalArgumentException}, a package name that is not a single folder name, a table code
 * that does not give a plain file name (path separators, {@code .}, {@code ..}, or the name of {@code parameters.csv}
 * or {@code FilingIndicators.csv}), a table without columns and tables whose codes give the same file name.
 *
 * <p>{@link #write(ReportPackage, OutputStream)} leaves the caller's stream open.
 */
public final class XbrlCsvWriter {

    public static final String PACKAGE_DOCUMENT_TYPE = "https://xbrl.org/report-package/2023";
    public static final String REPORT_DOCUMENT_TYPE = "https://xbrl.org/2021/xbrl-csv";

    /** Written as a plain DOS timestamp, which carries no time zone, so the bytes do not depend on the JVM's. */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] write(ReportPackage reportPackage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(reportPackage, out);
        return out.toByteArray();
    }

    public void write(ReportPackage reportPackage, OutputStream out) {
        checkWritable(reportPackage);
        String root = reportPackage.name() + "/";
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(out), StandardCharsets.UTF_8)) {
            add(zip, root + "META-INF/reportPackage.json", reportPackageJson());
            add(zip, root + "reports/report.json", reportJson(reportPackage.entryPoint()));
            add(zip, root + "reports/parameters.csv", Csv.write(
                    List.of("name", "value"),
                    reportPackage.parameters().values().entrySet().stream()
                            .map(e -> List.of(e.getKey(), e.getValue()))
                            .toList()));
            add(zip, root + "reports/FilingIndicators.csv", Csv.write(
                    List.of("templateID", "reported"),
                    reportPackage.filingIndicators().stream()
                            .map(i -> List.of(i.templateId(), Boolean.toString(i.reported())))
                            .toList()));
            for (Table table : reportPackage.tables()) {
                add(zip, root + "reports/" + table.fileName(), Csv.write(table.columns(), table.rows()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write the xBRL-CSV package", e);
        }
    }

    private static void checkWritable(ReportPackage reportPackage) {
        String name = reportPackage.name();
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid package name, a single folder name is expected: " + name);
        }
        Set<String> fileNames = new HashSet<>();
        for (Table table : reportPackage.tables()) {
            checkTableCode(table);
            if (table.columns().isEmpty()) {
                // Its header and rows would be blank lines, which reading ignores: the table would vanish.
                throw new IllegalArgumentException("Table without columns, it cannot be written: " + table.code());
            }
            if (!fileNames.add(table.fileName())) {
                throw new IllegalArgumentException("Two tables would be written to the same file: " + table.fileName());
            }
        }
    }

    private static void checkTableCode(Table table) {
        String code = table.code();
        String fileName = table.fileName();
        if (code.isEmpty() || code.equals(".") || code.equals("..") || code.contains("/") || code.contains("\\")
                || fileName.equalsIgnoreCase("parameters.csv") || fileName.equalsIgnoreCase("FilingIndicators.csv")) {
            throw new IllegalArgumentException("Invalid table code, it must give a plain file name: " + code);
        }
    }

    private String reportPackageJson() throws IOException {
        ObjectNode root = json.createObjectNode();
        root.putObject("documentInfo").put("documentType", PACKAGE_DOCUMENT_TYPE);
        return json.writeValueAsString(root);
    }

    private String reportJson(String entryPoint) throws IOException {
        ObjectNode root = json.createObjectNode();
        ObjectNode info = root.putObject("documentInfo");
        info.put("documentType", REPORT_DOCUMENT_TYPE);
        info.putArray("extends").add(entryPoint);
        return json.writeValueAsString(root);
    }

    private static void add(ZipOutputStream zip, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTimeLocal(FIXED_TIME);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Closing the archive must not close the caller's stream; writes are passed through in blocks. */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}

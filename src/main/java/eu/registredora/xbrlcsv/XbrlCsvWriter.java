package eu.registredora.xbrlcsv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
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
 * bytes, hence the same hash.
 */
public final class XbrlCsvWriter {

    public static final String PACKAGE_DOCUMENT_TYPE = "https://xbrl.org/report-package/2023";
    public static final String REPORT_DOCUMENT_TYPE = "https://xbrl.org/2021/xbrl-csv";

    private static final FileTime FIXED_TIME = FileTime.from(Instant.parse("2000-01-01T00:00:00Z"));

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] write(ReportPackage reportPackage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(reportPackage, out);
        return out.toByteArray();
    }

    public void write(ReportPackage reportPackage, OutputStream out) {
        String root = reportPackage.name() + "/";
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
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
        entry.setLastModifiedTime(FIXED_TIME);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

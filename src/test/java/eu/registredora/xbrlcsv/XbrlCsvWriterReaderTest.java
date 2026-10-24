package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XbrlCsvWriterReaderTest {

    private final XbrlCsvWriter writer = new XbrlCsvWriter();
    private final XbrlCsvReader reader = new XbrlCsvReader();

    @Test
    void writes_the_expected_structure() throws Exception {
        Map<String, String> files = content(writer.write(ReportPackageFixtures.simplePackage()));
        String root = "506700GE1G29325QX363_2026-12-31/";

        assertThat(files.keySet()).containsExactly(
                root + "META-INF/reportPackage.json",
                root + "reports/report.json",
                root + "reports/parameters.csv",
                root + "reports/FilingIndicators.csv",
                root + "reports/b_01.01.csv",
                root + "reports/b_05.01.csv");
        assertThat(files.get(root + "META-INF/reportPackage.json")).contains(XbrlCsvWriter.PACKAGE_DOCUMENT_TYPE);
        assertThat(files.get(root + "reports/report.json"))
                .contains(XbrlCsvWriter.REPORT_DOCUMENT_TYPE)
                .contains(ReportPackageFixtures.ENTRY_POINT);
        assertThat(files.get(root + "reports/parameters.csv"))
                .isEqualTo("name,value\r\nentityID,rs:506700GE1G29325QX363.CON\r\nrefPeriod,2026-12-31\r\nbaseCurrency,EUR\r\n");
        assertThat(files.get(root + "reports/FilingIndicators.csv"))
                .isEqualTo("templateID,reported\r\nB_01.01,true\r\nB_05.01,true\r\n");
        assertThat(files.get(root + "reports/b_05.01.csv"))
                .isEqualTo("c0010,c0020,c0050\r\n5493001KJTIIGC8Y1R12,LEI,\"Cloud, \"\"Nimbus\"\" SA\"\r\n");
    }

    @Test
    void always_produces_the_same_bytes() {
        ReportPackage reportPackage = ReportPackageFixtures.simplePackage();
        assertThat(writer.write(reportPackage)).isEqualTo(writer.write(reportPackage));
    }

    @Test
    void produces_the_same_bytes_in_every_time_zone() {
        ReportPackage reportPackage = ReportPackageFixtures.simplePackage();
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            byte[] utc = writer.write(reportPackage);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            byte[] newYork = writer.write(reportPackage);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            byte[] tokyo = writer.write(reportPackage);

            assertThat(newYork).isEqualTo(utc);
            assertThat(tokyo).isEqualTo(utc);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void reads_back_empty_rows_of_a_single_column_table() {
        Table table = Table.builder("B_01.01", "c0010").row("").row("x").row("").build();
        ReportPackage original = ReportPackage.withDerivedFilingIndicators("p", "http://x",
                Parameters.registerOfInformation("rs:X.CON", LocalDate.of(2026, 12, 31), "EUR"), List.of(table));

        assertThat(reader.read(writer.write(original)).tables()).containsExactly(table);
    }

    @Test
    void refuses_tables_that_would_share_a_file_name() {
        Table upper = Table.builder("B_01.01", "c0010").build();
        Table lower = Table.builder("b_01.01", "c0010").build();
        ReportPackage reportPackage = new ReportPackage("p", "http://x", new Parameters(Map.of()),
                List.of(), List.of(upper, lower));

        assertThatThrownBy(() -> writer.write(reportPackage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("b_01.01.csv");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a/b", "..", "a\\b"})
    void refuses_a_package_name_that_is_not_a_single_folder(String name) {
        ReportPackage reportPackage = new ReportPackage(name, "http://x", new Parameters(Map.of()), List.of(), List.of());

        assertThatThrownBy(() -> writer.write(reportPackage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("package name");
    }

    @Test
    void reads_back_a_written_package() {
        ReportPackage original = ReportPackageFixtures.simplePackage();
        ReportPackage read = reader.read(writer.write(original));

        assertThat(read.name()).isEqualTo(original.name());
        assertThat(read.entryPoint()).isEqualTo(original.entryPoint());
        assertThat(read.parameters()).isEqualTo(original.parameters());
        assertThat(read.filingIndicators()).isEqualTo(original.filingIndicators());
        assertThat(read.tables()).isEqualTo(original.tables());
    }

    @Test
    void rejects_an_archive_without_report_json() throws Exception {
        byte[] archive = zip(Map.of("x/reports/b_01.01.csv", "c0010\r\n"));
        assertThatThrownBy(() -> reader.read(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("report.json");
    }

    @Test
    void rejects_a_path_climbing_out() throws Exception {
        byte[] archive = zip(Map.of("../evil/reports/report.json", "{}"));
        assertThatThrownBy(() -> reader.read(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden path");
    }

    @Test
    void rejects_an_archive_with_too_many_entries() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i <= 500; i++) {
            files.put("x/reports/f" + i + ".csv", "c0010\r\n");
        }
        assertThatThrownBy(() -> reader.read(zip(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many entries");
    }

    @Test
    void rejects_an_archive_too_large_once_uncompressed() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("x/reports/b_01.01.csv"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i <= 200; i++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }
        assertThatThrownBy(() -> reader.read(out.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uncompressed size too large");
    }

    @Test
    void rejects_an_archive_with_two_entries_of_the_same_name() throws Exception {
        byte[] archive = zip(Map.of("x/reports/report.json", REPORT_JSON, "x/reports/a.csv", "c0010\r\n",
                "x/reports/b.csv", "c0010\r\n"));
        byte[] duplicated = replaceAll(archive, "x/reports/b.csv", "x/reports/a.csv");
        assertThatThrownBy(() -> reader.read(duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate entry x/reports/a.csv");
    }

    @Test
    void rejects_an_archive_with_two_reports() throws Exception {
        byte[] archive = zip(Map.of("x/reports/report.json", REPORT_JSON, "y/reports/report.json", REPORT_JSON));
        assertThatThrownBy(() -> reader.read(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("several reports/report.json");
    }

    @Test
    void rejects_a_reported_value_other_than_true_or_false() throws Exception {
        byte[] archive = zip(Map.of("x/reports/report.json", REPORT_JSON,
                "x/reports/FilingIndicators.csv", "templateID,reported\r\nB_01.01,yes\r\n"));
        assertThatThrownBy(() -> reader.read(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yes");
    }

    @Test
    void rejects_what_is_not_an_archive() {
        assertThatThrownBy(() -> reader.read("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final String REPORT_JSON = "{\"documentInfo\":{\"extends\":[\"http://x\"]}}";

    private static byte[] replaceAll(byte[] bytes, String from, String to) {
        String latin1 = new String(bytes, StandardCharsets.ISO_8859_1);
        return latin1.replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Map<String, String> content(byte[] archive) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}

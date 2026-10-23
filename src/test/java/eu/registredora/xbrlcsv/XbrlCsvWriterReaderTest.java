package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
    void rejects_what_is_not_an_archive() {
        assertThatThrownBy(() -> reader.read("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
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

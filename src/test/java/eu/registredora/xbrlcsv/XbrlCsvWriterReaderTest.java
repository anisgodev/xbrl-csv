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
    void ecrit_la_structure_attendue() throws Exception {
        Map<String, String> fichiers = contenu(writer.ecrire(PaquetDeRapportFixtures.paquetSimple()));
        String racine = "506700GE1G29325QX363_2026-12-31/";

        assertThat(fichiers.keySet()).containsExactly(
                racine + "META-INF/reportPackage.json",
                racine + "reports/report.json",
                racine + "reports/parameters.csv",
                racine + "reports/FilingIndicators.csv",
                racine + "reports/b_01.01.csv",
                racine + "reports/b_05.01.csv");
        assertThat(fichiers.get(racine + "META-INF/reportPackage.json")).contains(XbrlCsvWriter.TYPE_PAQUET);
        assertThat(fichiers.get(racine + "reports/report.json"))
                .contains(XbrlCsvWriter.TYPE_RAPPORT)
                .contains(PaquetDeRapportFixtures.POINT_ENTREE);
        assertThat(fichiers.get(racine + "reports/parameters.csv"))
                .isEqualTo("name,value\r\nentityID,rs:506700GE1G29325QX363.CON\r\nrefPeriod,2026-12-31\r\nbaseCurrency,EUR\r\n");
        assertThat(fichiers.get(racine + "reports/FilingIndicators.csv"))
                .isEqualTo("templateID,reported\r\nB_01.01,true\r\nB_05.01,true\r\n");
        assertThat(fichiers.get(racine + "reports/b_05.01.csv"))
                .isEqualTo("c0010,c0020,c0050\r\n5493001KJTIIGC8Y1R12,LEI,\"Nuage, \"\"Cloud\"\" SA\"\r\n");
    }

    @Test
    void produit_toujours_les_memes_octets() {
        PaquetDeRapport paquet = PaquetDeRapportFixtures.paquetSimple();
        assertThat(writer.ecrire(paquet)).isEqualTo(writer.ecrire(paquet));
    }

    @Test
    void relit_un_paquet_ecrit() {
        PaquetDeRapport origine = PaquetDeRapportFixtures.paquetSimple();
        PaquetDeRapport relu = reader.lire(writer.ecrire(origine));

        assertThat(relu.nom()).isEqualTo(origine.nom());
        assertThat(relu.pointEntree()).isEqualTo(origine.pointEntree());
        assertThat(relu.parametres()).isEqualTo(origine.parametres());
        assertThat(relu.indicateurs()).isEqualTo(origine.indicateurs());
        assertThat(relu.tables()).isEqualTo(origine.tables());
    }

    @Test
    void refuse_une_archive_sans_report_json() throws Exception {
        byte[] archive = zip(Map.of("x/reports/b_01.01.csv", "c0010\r\n"));
        assertThatThrownBy(() -> reader.lire(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("report.json");
    }

    @Test
    void refuse_un_chemin_qui_remonte() throws Exception {
        byte[] archive = zip(Map.of("../evil/reports/report.json", "{}"));
        assertThatThrownBy(() -> reader.lire(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chemin interdit");
    }

    @Test
    void refuse_ce_qui_n_est_pas_une_archive() {
        assertThatThrownBy(() -> reader.lire("pas un zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, String> contenu(byte[] archive) throws Exception {
        Map<String, String> fichiers = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entree;
            while ((entree = zip.getNextEntry()) != null) {
                fichiers.put(entree.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return fichiers;
    }

    private static byte[] zip(Map<String, String> fichiers) throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (Map.Entry<String, String> f : fichiers.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return sortie.toByteArray();
    }
}

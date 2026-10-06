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
 * Écrit un {@link PaquetDeRapport} sous forme d'archive ZIP de paquet de rapport xBRL-CSV :
 *
 * <pre>
 * {nom}/META-INF/reportPackage.json
 * {nom}/reports/report.json
 * {nom}/reports/parameters.csv
 * {nom}/reports/FilingIndicators.csv
 * {nom}/reports/b_01.01.csv ...
 * </pre>
 *
 * <p>Les fichiers sont en UTF-8 sans BOM. L'ordre des entrées et leurs dates sont fixes : un même paquet produit
 * toujours les mêmes octets, donc la même empreinte.
 */
public final class XbrlCsvWriter {

    public static final String TYPE_PAQUET = "https://xbrl.org/report-package/2023";
    public static final String TYPE_RAPPORT = "https://xbrl.org/2021/xbrl-csv";

    private static final FileTime DATE_FIXE = FileTime.from(Instant.parse("2000-01-01T00:00:00Z"));

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] ecrire(PaquetDeRapport paquet) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ecrire(paquet, sortie);
        return sortie.toByteArray();
    }

    public void ecrire(PaquetDeRapport paquet, OutputStream sortie) {
        String racine = paquet.nom() + "/";
        try (ZipOutputStream zip = new ZipOutputStream(sortie, StandardCharsets.UTF_8)) {
            ajouter(zip, racine + "META-INF/reportPackage.json", reportPackageJson());
            ajouter(zip, racine + "reports/report.json", reportJson(paquet.pointEntree()));
            ajouter(zip, racine + "reports/parameters.csv", Csv.ecrire(
                    List.of("name", "value"),
                    paquet.parametres().valeurs().entrySet().stream()
                            .map(e -> List.of(e.getKey(), e.getValue()))
                            .toList()));
            ajouter(zip, racine + "reports/FilingIndicators.csv", Csv.ecrire(
                    List.of("templateID", "reported"),
                    paquet.indicateurs().stream()
                            .map(i -> List.of(i.modele(), Boolean.toString(i.declare())))
                            .toList()));
            for (Table table : paquet.tables()) {
                ajouter(zip, racine + "reports/" + table.nomFichier(), Csv.ecrire(table.colonnes(), table.lignes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du paquet xBRL-CSV impossible", e);
        }
    }

    private String reportPackageJson() throws IOException {
        ObjectNode racine = json.createObjectNode();
        racine.putObject("documentInfo").put("documentType", TYPE_PAQUET);
        return json.writeValueAsString(racine);
    }

    private String reportJson(String pointEntree) throws IOException {
        ObjectNode racine = json.createObjectNode();
        ObjectNode info = racine.putObject("documentInfo");
        info.put("documentType", TYPE_RAPPORT);
        info.putArray("extends").add(pointEntree);
        return json.writeValueAsString(racine);
    }

    private static void ajouter(ZipOutputStream zip, String chemin, String contenu) throws IOException {
        ZipEntry entree = new ZipEntry(chemin);
        entree.setLastModifiedTime(DATE_FIXE);
        zip.putNextEntry(entree);
        zip.write(contenu.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

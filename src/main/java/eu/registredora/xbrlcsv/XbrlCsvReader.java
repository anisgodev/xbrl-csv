package eu.registredora.xbrlcsv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
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
 * Relit une archive de paquet de rapport xBRL-CSV, par exemple le dépôt de l'année précédente d'un client.
 *
 * <p>Protège contre les archives malveillantes : taille décompressée et nombre d'entrées limités, chemins
 * remontant l'arborescence refusés.
 */
public final class XbrlCsvReader {

    private static final long TAILLE_MAX_DECOMPRESSEE = 200L * 1024 * 1024;
    private static final int ENTREES_MAX = 500;

    private final ObjectMapper json = new ObjectMapper();

    public PaquetDeRapport lire(byte[] archive) {
        return lire(new ByteArrayInputStream(archive));
    }

    public PaquetDeRapport lire(InputStream archive) {
        Map<String, String> fichiers = decompresser(archive);

        String cheminRapport = fichiers.keySet().stream()
                .filter(c -> c.endsWith("/reports/report.json"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Paquet invalide : reports/report.json absent"));
        String prefixe = cheminRapport.substring(0, cheminRapport.length() - "report.json".length());
        String nom = cheminRapport.substring(0, cheminRapport.indexOf('/'));

        String pointEntree = lirePointEntree(fichiers.get(cheminRapport));
        Parametres parametres = lireParametres(fichiers.get(prefixe + "parameters.csv"));
        List<IndicateurDeDepot> indicateurs = lireIndicateurs(fichiers.get(prefixe + "FilingIndicators.csv"));

        List<Table> tables = new ArrayList<>();
        new TreeMap<>(fichiers).forEach((chemin, contenu) -> {
            if (!chemin.startsWith(prefixe) || !chemin.endsWith(".csv")) {
                return;
            }
            String fichier = chemin.substring(prefixe.length());
            if (fichier.contains("/") || fichier.equals("parameters.csv") || fichier.equals("FilingIndicators.csv")) {
                return;
            }
            List<List<String>> lignes = Csv.lire(contenu);
            if (lignes.isEmpty()) {
                return;
            }
            tables.add(new Table(Table.codeDepuisFichier(fichier), lignes.get(0), lignes.subList(1, lignes.size())));
        });

        return new PaquetDeRapport(nom, pointEntree, parametres, indicateurs, tables);
    }

    private String lirePointEntree(String reportJson) {
        try {
            JsonNode extendsNode = json.readTree(reportJson).path("documentInfo").path("extends");
            if (!extendsNode.isArray() || extendsNode.isEmpty()) {
                throw new IllegalArgumentException("Paquet invalide : documentInfo.extends absent de report.json");
            }
            return extendsNode.get(0).asText();
        } catch (IOException e) {
            throw new IllegalArgumentException("Paquet invalide : report.json illisible", e);
        }
    }

    private static Parametres lireParametres(String contenu) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        if (contenu != null) {
            List<List<String>> lignes = Csv.lire(contenu);
            for (List<String> ligne : lignes.subList(Math.min(1, lignes.size()), lignes.size())) {
                if (ligne.size() >= 2) {
                    valeurs.put(ligne.get(0), ligne.get(1));
                }
            }
        }
        return new Parametres(valeurs);
    }

    private static List<IndicateurDeDepot> lireIndicateurs(String contenu) {
        List<IndicateurDeDepot> indicateurs = new ArrayList<>();
        if (contenu != null) {
            List<List<String>> lignes = Csv.lire(contenu);
            for (List<String> ligne : lignes.subList(Math.min(1, lignes.size()), lignes.size())) {
                if (ligne.size() >= 2) {
                    indicateurs.add(new IndicateurDeDepot(ligne.get(0), Boolean.parseBoolean(ligne.get(1).strip())));
                }
            }
        }
        return indicateurs;
    }

    private static Map<String, String> decompresser(InputStream archive) {
        Map<String, String> fichiers = new LinkedHashMap<>();
        long total = 0;
        int entrees = 0;
        try (ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
            ZipEntry entree;
            byte[] tampon = new byte[8192];
            while ((entree = zip.getNextEntry()) != null) {
                if (++entrees > ENTREES_MAX) {
                    throw new IllegalArgumentException("Paquet invalide : trop d'entrées");
                }
                String chemin = entree.getName().replace('\\', '/');
                if (chemin.startsWith("/") || chemin.contains("../")) {
                    throw new IllegalArgumentException("Paquet invalide : chemin interdit " + chemin);
                }
                if (entree.isDirectory()) {
                    continue;
                }
                java.io.ByteArrayOutputStream contenu = new java.io.ByteArrayOutputStream();
                int lus;
                while ((lus = zip.read(tampon)) > 0) {
                    total += lus;
                    if (total > TAILLE_MAX_DECOMPRESSEE) {
                        throw new IllegalArgumentException("Paquet invalide : taille décompressée excessive");
                    }
                    contenu.write(tampon, 0, lus);
                }
                fichiers.put(chemin, contenu.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du paquet xBRL-CSV impossible", e);
        }
        if (fichiers.isEmpty()) {
            throw new IllegalArgumentException("Paquet invalide : archive vide ou non ZIP");
        }
        return fichiers;
    }
}

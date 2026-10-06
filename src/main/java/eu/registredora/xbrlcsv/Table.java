package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Un tableau du rapport (par exemple {@code B_01.01}) : des codes de colonnes et des lignes de valeurs.
 * Une valeur absente est une chaîne vide.
 */
public record Table(String code, List<String> colonnes, List<List<String>> lignes) {

    public Table {
        Objects.requireNonNull(code, "code");
        colonnes = List.copyOf(colonnes);
        lignes = lignes.stream().map(l -> l.stream().map(v -> v == null ? "" : v).toList()).toList();
    }

    public static Builder builder(String code, String... colonnes) {
        return new Builder(code, List.of(colonnes));
    }

    public static Builder builder(String code, List<String> colonnes) {
        return new Builder(code, colonnes);
    }

    /** Nom du fichier CSV dans le paquet : le code en minuscules, par exemple {@code b_01.01.csv}. */
    public String nomFichier() {
        return nomFichier(code);
    }

    public static String nomFichier(String code) {
        return code.toLowerCase(Locale.ROOT) + ".csv";
    }

    /** Code du tableau déduit d'un nom de fichier, par exemple {@code b_01.01.csv} donne {@code B_01.01}. */
    public static String codeDepuisFichier(String nomFichier) {
        String base = nomFichier.endsWith(".csv") ? nomFichier.substring(0, nomFichier.length() - 4) : nomFichier;
        return base.toUpperCase(Locale.ROOT);
    }

    public int indexColonne(String colonne) {
        return colonnes.indexOf(colonne);
    }

    public static final class Builder {
        private final String code;
        private final List<String> colonnes;
        private final List<List<String>> lignes = new ArrayList<>();

        private Builder(String code, List<String> colonnes) {
            this.code = code;
            this.colonnes = List.copyOf(colonnes);
        }

        public Builder ligne(String... valeurs) {
            lignes.add(new ArrayList<>(List.of(valeurs)));
            return this;
        }

        public Builder ligne(List<String> valeurs) {
            lignes.add(new ArrayList<>(valeurs));
            return this;
        }

        public Table build() {
            return new Table(code, colonnes, lignes);
        }
    }
}

package eu.registredora.xbrlcsv;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Legal Entity Identifier (ISO 17442) : 18 caractères alphanumériques suivis de 2 chiffres de contrôle
 * calculés selon ISO 7064 MOD 97-10.
 */
public record Lei(String valeur) {

    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{18}[0-9]{2}");

    public Lei {
        Objects.requireNonNull(valeur, "valeur");
        if (!estValide(valeur)) {
            throw new IllegalArgumentException("LEI invalide : " + valeur);
        }
    }

    public static Lei of(String valeur) {
        return new Lei(valeur == null ? null : valeur.strip().toUpperCase());
    }

    /** Vrai si la chaîne a le format d'un LEI et une clé de contrôle correcte. */
    public static boolean estValide(String valeur) {
        if (valeur == null || !FORMAT.matcher(valeur).matches()) {
            return false;
        }
        return reste97(valeur) == 1;
    }

    /** Calcule les deux chiffres de contrôle pour un préfixe de 18 caractères. */
    public static String cleDeControle(String prefixe18) {
        if (prefixe18 == null || !prefixe18.matches("[0-9A-Z]{18}")) {
            throw new IllegalArgumentException("Le préfixe doit contenir 18 caractères alphanumériques majuscules");
        }
        int cle = 98 - reste97(prefixe18 + "00");
        return String.format("%02d", cle);
    }

    private static int reste97(String s) {
        int reste = 0;
        for (int i = 0; i < s.length(); i++) {
            int chiffre = Character.digit(s.charAt(i), 36);
            reste = (chiffre > 9 ? reste * 100 + chiffre : reste * 10 + chiffre) % 97;
        }
        return reste;
    }

    @Override
    public String toString() {
        return valeur;
    }
}

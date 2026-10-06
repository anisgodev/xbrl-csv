package eu.registredora.xbrlcsv;

/**
 * Résultat d'un contrôle structurel.
 *
 * @param gravite {@link Gravite#BLOQUANT} empêche le dépôt, {@link Gravite#ALERTE} est à examiner
 * @param code    identifiant stable de la règle, par exemple {@code XC-TABLE-COLONNES}
 * @param table   code du tableau concerné, ou {@code null}
 * @param ligne   numéro de ligne de données (1 = première ligne après l'en-tête), ou {@code null}
 * @param colonne code de colonne concerné, ou {@code null}
 * @param texte   explication lisible
 */
public record MessageDeValidation(Gravite gravite, String code, String table, Integer ligne, String colonne,
                                  String texte) {

    public enum Gravite {
        BLOQUANT,
        ALERTE
    }

    static MessageDeValidation bloquant(String code, String table, Integer ligne, String colonne, String texte) {
        return new MessageDeValidation(Gravite.BLOQUANT, code, table, ligne, colonne, texte);
    }

    static MessageDeValidation alerte(String code, String table, Integer ligne, String colonne, String texte) {
        return new MessageDeValidation(Gravite.ALERTE, code, table, ligne, colonne, texte);
    }
}

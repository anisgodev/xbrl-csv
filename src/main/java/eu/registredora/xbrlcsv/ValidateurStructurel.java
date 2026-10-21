package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contrôles structurels d'un paquet, indépendants de la taxonomie : ce qui ferait rejeter un paquet avant même la
 * lecture de son contenu. Les règles de la taxonomie EBA sont vérifiées ailleurs (Arelle, voir ADR-0003).
 */
public final class ValidateurStructurel {

    /** Codes des règles appliquées, pour annoncer combien de règles un contrôle a vérifiées. */
    public static final List<String> REGLES = List.of(
            "XC-NOM-PAQUET", "XC-POINT-ENTREE", "XC-PARAM-ENTITE", "XC-PARAM-PERIODE", "XC-PARAM-DEVISE",
            "XC-INDICATEUR-DOUBLON", "XC-TABLE-DOUBLON", "XC-TABLE-NON-DECLAREE", "XC-TABLE-ABSENTE", "XC-TABLE-CODE",
            "XC-TABLE-COLONNES", "XC-COLONNE-VIDE", "XC-COLONNE-DOUBLON", "XC-LIGNE-LARGEUR", "XC-LIGNE-VIDE");

    private static final Pattern NOM_PAQUET = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern CODE_TABLE = Pattern.compile("[A-Z]_[0-9]{2}\\.[0-9]{2}");
    private static final Pattern DEVISE = Pattern.compile("(iso4217:)?[A-Z]{3}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    public List<MessageDeValidation> valider(PaquetDeRapport paquet) {
        List<MessageDeValidation> messages = new ArrayList<>();
        validerNom(paquet, messages);
        validerParametres(paquet.parametres(), messages);
        validerIndicateurs(paquet, messages);
        for (Table table : paquet.tables()) {
            validerTable(table, messages);
        }
        return messages;
    }

    private static void validerNom(PaquetDeRapport paquet, List<MessageDeValidation> messages) {
        if (!NOM_PAQUET.matcher(paquet.nom()).matches()) {
            messages.add(MessageDeValidation.bloquant("XC-NOM-PAQUET", null, null, null,
                    "Le nom du paquet ne doit contenir que lettres, chiffres, point, tiret et soulignement : "
                            + paquet.nom()));
        }
        if (paquet.pointEntree().isBlank()) {
            messages.add(MessageDeValidation.bloquant("XC-POINT-ENTREE", null, null, null,
                    "Le point d'entrée de la taxonomie est vide"));
        }
    }

    private static void validerParametres(Parametres parametres, List<MessageDeValidation> messages) {
        String entite = parametres.valeur(Parametres.ENTITY_ID);
        if (entite == null || entite.isBlank()) {
            messages.add(MessageDeValidation.bloquant("XC-PARAM-ENTITE", null, null, null,
                    "Paramètre entityID absent"));
        }
        String periode = parametres.valeur(Parametres.REF_PERIOD);
        if (periode == null || !DATE.matcher(periode).matches()) {
            messages.add(MessageDeValidation.bloquant("XC-PARAM-PERIODE", null, null, null,
                    "Paramètre refPeriod absent ou pas au format AAAA-MM-JJ : " + periode));
        }
        String devise = parametres.valeur(Parametres.BASE_CURRENCY);
        if (devise != null && !DEVISE.matcher(devise).matches()) {
            messages.add(MessageDeValidation.bloquant("XC-PARAM-DEVISE", null, null, null,
                    "Paramètre baseCurrency invalide (code ISO 4217 attendu) : " + devise));
        }
    }

    private static void validerIndicateurs(PaquetDeRapport paquet, List<MessageDeValidation> messages) {
        Set<String> declares = new HashSet<>();
        Set<String> vus = new HashSet<>();
        for (IndicateurDeDepot indicateur : paquet.indicateurs()) {
            if (!vus.add(indicateur.modele())) {
                messages.add(MessageDeValidation.bloquant("XC-INDICATEUR-DOUBLON", indicateur.modele(), null, null,
                        "Indicateur de dépôt en double"));
            }
            if (indicateur.declare()) {
                declares.add(indicateur.modele());
            }
        }
        Set<String> presents = new HashSet<>();
        for (Table table : paquet.tables()) {
            if (!presents.add(table.code())) {
                messages.add(MessageDeValidation.bloquant("XC-TABLE-DOUBLON", table.code(), null, null,
                        "Tableau présent deux fois"));
            }
            if (!declares.contains(table.code())) {
                messages.add(MessageDeValidation.bloquant("XC-TABLE-NON-DECLAREE", table.code(), null, null,
                        "Tableau présent mais non déclaré dans FilingIndicators.csv"));
            }
        }
        for (String modele : declares) {
            if (!presents.contains(modele)) {
                messages.add(MessageDeValidation.bloquant("XC-TABLE-ABSENTE", modele, null, null,
                        "Tableau déclaré dans FilingIndicators.csv mais absent du paquet"));
            }
        }
    }

    private static void validerTable(Table table, List<MessageDeValidation> messages) {
        String code = table.code();
        if (!CODE_TABLE.matcher(code).matches()) {
            messages.add(MessageDeValidation.bloquant("XC-TABLE-CODE", code, null, null,
                    "Code de tableau inattendu (forme attendue : B_01.01)"));
        }
        if (table.colonnes().isEmpty()) {
            messages.add(MessageDeValidation.bloquant("XC-TABLE-COLONNES", code, null, null,
                    "Le tableau n'a pas de colonnes"));
            return;
        }
        Set<String> colonnes = new HashSet<>();
        for (String colonne : table.colonnes()) {
            if (colonne.isBlank()) {
                messages.add(MessageDeValidation.bloquant("XC-COLONNE-VIDE", code, null, null,
                        "Nom de colonne vide"));
            } else if (!colonnes.add(colonne)) {
                messages.add(MessageDeValidation.bloquant("XC-COLONNE-DOUBLON", code, null, colonne,
                        "Colonne en double"));
            }
        }
        for (int i = 0; i < table.lignes().size(); i++) {
            List<String> ligne = table.lignes().get(i);
            if (ligne.size() != table.colonnes().size()) {
                messages.add(MessageDeValidation.bloquant("XC-LIGNE-LARGEUR", code, i + 1, null,
                        "La ligne a " + ligne.size() + " valeurs pour " + table.colonnes().size() + " colonnes"));
            } else if (ligne.stream().allMatch(String::isBlank)) {
                messages.add(MessageDeValidation.alerte("XC-LIGNE-VIDE", code, i + 1, null,
                        "Ligne entièrement vide"));
            }
        }
    }
}

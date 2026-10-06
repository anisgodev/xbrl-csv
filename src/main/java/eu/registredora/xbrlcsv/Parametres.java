package eu.registredora.xbrlcsv;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Paramètres du rapport, écrits dans {@code parameters.csv} (colonnes {@code name,value}).
 *
 * <p>Les noms et formats exacts (identifiant de l'entité, décimales) sont fixés par les règles de dépôt de l'EBA
 * pour la version de taxonomie utilisée ; la bibliothèque les transmet tels quels.
 */
public record Parametres(Map<String, String> valeurs) {

    public static final String ENTITY_ID = "entityID";
    public static final String REF_PERIOD = "refPeriod";
    public static final String BASE_CURRENCY = "baseCurrency";

    public Parametres {
        valeurs = Collections.unmodifiableMap(new LinkedHashMap<>(valeurs));
    }

    /** Paramètres minimaux d'un registre d'information : identifiant de l'entité, date de référence, devise. */
    public static Parametres registre(String entityId, LocalDate dateReference, String devise) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(ENTITY_ID, Objects.requireNonNull(entityId, "entityId"));
        v.put(REF_PERIOD, Objects.requireNonNull(dateReference, "dateReference").toString());
        v.put(BASE_CURRENCY, Objects.requireNonNull(devise, "devise"));
        return new Parametres(v);
    }

    public Parametres avec(String nom, String valeur) {
        Map<String, String> v = new LinkedHashMap<>(valeurs);
        v.put(nom, valeur);
        return new Parametres(v);
    }

    public String valeur(String nom) {
        return valeurs.get(nom);
    }
}

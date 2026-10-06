package eu.registredora.xbrlcsv;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un paquet de rapport xBRL-CSV complet.
 *
 * @param nom         nom du dossier racine dans l'archive (sans extension)
 * @param pointEntree URL du point d'entrée JSON de la taxonomie, référencée par {@code extends} dans {@code report.json}
 * @param parametres  contenu de {@code parameters.csv}
 * @param indicateurs contenu de {@code FilingIndicators.csv}
 * @param tables      les tableaux déclarés
 */
public record PaquetDeRapport(
        String nom,
        String pointEntree,
        Parametres parametres,
        List<IndicateurDeDepot> indicateurs,
        List<Table> tables) {

    public PaquetDeRapport {
        Objects.requireNonNull(nom, "nom");
        Objects.requireNonNull(pointEntree, "pointEntree");
        Objects.requireNonNull(parametres, "parametres");
        indicateurs = List.copyOf(indicateurs);
        tables = List.copyOf(tables);
    }

    /**
     * Construit un paquet dont les indicateurs de dépôt découlent des tableaux fournis : chaque tableau présent est
     * déclaré.
     */
    public static PaquetDeRapport avecIndicateursDeduits(String nom, String pointEntree, Parametres parametres,
                                                        List<Table> tables) {
        List<IndicateurDeDepot> indicateurs = tables.stream()
                .map(t -> new IndicateurDeDepot(t.code(), true))
                .toList();
        return new PaquetDeRapport(nom, pointEntree, parametres, indicateurs, tables);
    }

    public Optional<Table> table(String code) {
        return tables.stream().filter(t -> t.code().equals(code)).findFirst();
    }
}

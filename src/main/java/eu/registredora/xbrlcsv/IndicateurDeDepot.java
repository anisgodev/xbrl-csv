package eu.registredora.xbrlcsv;

import java.util.Objects;

/** Ligne de {@code FilingIndicators.csv} : un modèle de tableau et s'il est déclaré. */
public record IndicateurDeDepot(String modele, boolean declare) {

    public IndicateurDeDepot {
        Objects.requireNonNull(modele, "modele");
    }
}

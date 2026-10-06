package eu.registredora.xbrlcsv;

import java.time.LocalDate;
import java.util.List;

final class PaquetDeRapportFixtures {

    static final String POINT_ENTREE = "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.0/mod/dora.json";

    private PaquetDeRapportFixtures() {
    }

    static PaquetDeRapport paquetSimple() {
        Table b0101 = Table.builder("B_01.01", "c0010", "c0020", "c0030")
                .ligne("506700GE1G29325QX363", "Assurance Exemple SA", "BE")
                .build();
        Table b0501 = Table.builder("B_05.01", "c0010", "c0020", "c0050")
                .ligne("5493001KJTIIGC8Y1R12", "LEI", "Nuage, \"Cloud\" SA")
                .build();
        return PaquetDeRapport.avecIndicateursDeduits(
                "506700GE1G29325QX363_2026-12-31",
                POINT_ENTREE,
                Parametres.registre("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR"),
                List.of(b0101, b0501));
    }
}

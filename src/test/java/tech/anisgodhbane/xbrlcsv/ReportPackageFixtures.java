package tech.anisgodhbane.xbrlcsv;

import java.time.LocalDate;
import java.util.List;

final class ReportPackageFixtures {

    static final String ENTRY_POINT = "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.0/mod/dora.json";

    private ReportPackageFixtures() {
    }

    static ReportPackage simplePackage() {
        Table b0101 = Table.builder("B_01.01", "c0010", "c0020", "c0030")
                .row("506700GE1G29325QX363", "Example Insurance SA", "BE")
                .build();
        Table b0501 = Table.builder("B_05.01", "c0010", "c0020", "c0050")
                .row("5493001KJTIIGC8Y1R12", "LEI", "Cloud, \"Nimbus\" SA")
                .build();
        return ReportPackage.withDerivedFilingIndicators(
                "506700GE1G29325QX363_2026-12-31",
                ENTRY_POINT,
                Parameters.registerOfInformation("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR"),
                List.of(b0101, b0501));
    }
}

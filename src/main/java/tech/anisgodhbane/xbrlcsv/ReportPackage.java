package tech.anisgodhbane.xbrlcsv;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A complete xBRL-CSV report package.
 *
 * @param name             name of the root folder in the archive (without extension)
 * @param entryPoint       URL of the taxonomy's JSON entry point, referenced by {@code extends} in {@code report.json}
 * @param parameters       content of {@code parameters.csv}
 * @param filingIndicators content of {@code FilingIndicators.csv}
 * @param tables           the reported tables
 */
public record ReportPackage(
        String name,
        String entryPoint,
        Parameters parameters,
        List<FilingIndicator> filingIndicators,
        List<Table> tables) {

    public ReportPackage {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(entryPoint, "entryPoint");
        Objects.requireNonNull(parameters, "parameters");
        filingIndicators = List.copyOf(filingIndicators);
        tables = List.copyOf(tables);
    }

    /**
     * Builds a package whose filing indicators follow from the given tables: every table present is reported.
     */
    public static ReportPackage withDerivedFilingIndicators(String name, String entryPoint, Parameters parameters,
                                                            List<Table> tables) {
        List<FilingIndicator> filingIndicators = tables.stream()
                .map(t -> new FilingIndicator(t.code(), true))
                .toList();
        return new ReportPackage(name, entryPoint, parameters, filingIndicators, tables);
    }

    public Optional<Table> table(String code) {
        return tables.stream().filter(t -> t.code().equals(code)).findFirst();
    }
}

package tech.anisgodhbane.xbrlcsv;

import java.util.Objects;

/** A row of {@code FilingIndicators.csv}: a table template and whether it is reported. */
public record FilingIndicator(String templateId, boolean reported) {

    public FilingIndicator {
        Objects.requireNonNull(templateId, "templateId");
    }
}

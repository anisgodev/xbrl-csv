package tech.anisgodhbane.xbrlcsv;

/**
 * Result of a structural check.
 *
 * @param severity {@link Severity#BLOCKING} prevents filing, {@link Severity#WARNING} should be reviewed
 * @param code     stable rule identifier, for example {@code XC-TABLE-COLUMNS}
 * @param table    code of the table concerned, or {@code null}
 * @param row      data row number (1 = first row after the header), or {@code null}
 * @param column   code of the column concerned, or {@code null}
 * @param text     human-readable explanation
 */
public record ValidationMessage(Severity severity, String code, String table, Integer row, String column,
                                String text) {

    public enum Severity {
        BLOCKING,
        WARNING
    }

    static ValidationMessage blocking(String code, String table, Integer row, String column, String text) {
        return new ValidationMessage(Severity.BLOCKING, code, table, row, column, text);
    }

    static ValidationMessage warning(String code, String table, Integer row, String column, String text) {
        return new ValidationMessage(Severity.WARNING, code, table, row, column, text);
    }
}

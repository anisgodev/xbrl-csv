package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural checks on a package, independent of the taxonomy: what would get a package rejected before its content
 * is even read. The EBA taxonomy rules are checked elsewhere (Arelle, see ADR-0003).
 */
public final class StructuralValidator {

    /** Codes of the rules applied, to report how many rules a check has verified. */
    public static final List<String> RULES = List.of(
            "XC-PACKAGE-NAME", "XC-ENTRY-POINT", "XC-PARAM-ENTITY", "XC-PARAM-PERIOD", "XC-PARAM-CURRENCY",
            "XC-INDICATOR-DUPLICATE", "XC-TABLE-DUPLICATE", "XC-TABLE-UNDECLARED", "XC-TABLE-MISSING", "XC-TABLE-CODE",
            "XC-TABLE-COLUMNS", "XC-COLUMN-EMPTY", "XC-COLUMN-DUPLICATE", "XC-ROW-WIDTH", "XC-ROW-EMPTY");

    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern TABLE_CODE = Pattern.compile("[A-Z]_[0-9]{2}\\.[0-9]{2}");
    private static final Pattern CURRENCY = Pattern.compile("(iso4217:)?[A-Z]{3}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    public List<ValidationMessage> validate(ReportPackage reportPackage) {
        List<ValidationMessage> messages = new ArrayList<>();
        validateName(reportPackage, messages);
        validateParameters(reportPackage.parameters(), messages);
        validateFilingIndicators(reportPackage, messages);
        for (Table table : reportPackage.tables()) {
            validateTable(table, messages);
        }
        return messages;
    }

    private static void validateName(ReportPackage reportPackage, List<ValidationMessage> messages) {
        if (!PACKAGE_NAME.matcher(reportPackage.name()).matches()) {
            messages.add(ValidationMessage.blocking("XC-PACKAGE-NAME", null, null, null,
                    "The package name may only contain letters, digits, dot, hyphen and underscore: "
                            + reportPackage.name()));
        }
        if (reportPackage.entryPoint().isBlank()) {
            messages.add(ValidationMessage.blocking("XC-ENTRY-POINT", null, null, null,
                    "The taxonomy entry point is empty"));
        }
    }

    private static void validateParameters(Parameters parameters, List<ValidationMessage> messages) {
        String entity = parameters.value(Parameters.ENTITY_ID);
        if (entity == null || entity.isBlank()) {
            messages.add(ValidationMessage.blocking("XC-PARAM-ENTITY", null, null, null,
                    "Parameter entityID is missing"));
        }
        String period = parameters.value(Parameters.REF_PERIOD);
        if (period == null || !DATE.matcher(period).matches()) {
            messages.add(ValidationMessage.blocking("XC-PARAM-PERIOD", null, null, null,
                    "Parameter refPeriod is missing or not in YYYY-MM-DD format: " + period));
        }
        String currency = parameters.value(Parameters.BASE_CURRENCY);
        if (currency != null && !CURRENCY.matcher(currency).matches()) {
            messages.add(ValidationMessage.blocking("XC-PARAM-CURRENCY", null, null, null,
                    "Parameter baseCurrency is invalid (ISO 4217 code expected): " + currency));
        }
    }

    private static void validateFilingIndicators(ReportPackage reportPackage, List<ValidationMessage> messages) {
        Set<String> reported = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (FilingIndicator indicator : reportPackage.filingIndicators()) {
            if (!seen.add(indicator.templateId())) {
                messages.add(ValidationMessage.blocking("XC-INDICATOR-DUPLICATE", indicator.templateId(), null, null,
                        "Duplicate filing indicator"));
            }
            if (indicator.reported()) {
                reported.add(indicator.templateId());
            }
        }
        Set<String> present = new HashSet<>();
        for (Table table : reportPackage.tables()) {
            if (!present.add(table.code())) {
                messages.add(ValidationMessage.blocking("XC-TABLE-DUPLICATE", table.code(), null, null,
                        "Table present twice"));
            }
            if (!reported.contains(table.code())) {
                messages.add(ValidationMessage.blocking("XC-TABLE-UNDECLARED", table.code(), null, null,
                        "Table present but not declared in FilingIndicators.csv"));
            }
        }
        for (String templateId : reported) {
            if (!present.contains(templateId)) {
                messages.add(ValidationMessage.blocking("XC-TABLE-MISSING", templateId, null, null,
                        "Table declared in FilingIndicators.csv but missing from the package"));
            }
        }
    }

    private static void validateTable(Table table, List<ValidationMessage> messages) {
        String code = table.code();
        if (!TABLE_CODE.matcher(code).matches()) {
            messages.add(ValidationMessage.blocking("XC-TABLE-CODE", code, null, null,
                    "Unexpected table code (expected form: B_01.01)"));
        }
        if (table.columns().isEmpty()) {
            messages.add(ValidationMessage.blocking("XC-TABLE-COLUMNS", code, null, null,
                    "The table has no columns"));
            return;
        }
        Set<String> columns = new HashSet<>();
        for (String column : table.columns()) {
            if (column.isBlank()) {
                messages.add(ValidationMessage.blocking("XC-COLUMN-EMPTY", code, null, null,
                        "Empty column name"));
            } else if (!columns.add(column)) {
                messages.add(ValidationMessage.blocking("XC-COLUMN-DUPLICATE", code, null, column,
                        "Duplicate column"));
            }
        }
        for (int i = 0; i < table.rows().size(); i++) {
            List<String> row = table.rows().get(i);
            if (row.size() != table.columns().size()) {
                messages.add(ValidationMessage.blocking("XC-ROW-WIDTH", code, i + 1, null,
                        "The row has " + row.size() + " values for " + table.columns().size() + " columns"));
            } else if (row.stream().allMatch(String::isBlank)) {
                messages.add(ValidationMessage.warning("XC-ROW-EMPTY", code, i + 1, null,
                        "Entirely empty row"));
            }
        }
    }
}

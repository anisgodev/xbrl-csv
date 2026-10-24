package tech.anisgodhbane.xbrlcsv;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Report parameters, written to {@code parameters.csv} (columns {@code name,value}).
 *
 * <p>The exact names and formats (entity identifier, decimals) are set by the EBA filing rules for the taxonomy
 * version in use; the library passes them through unchanged. A {@code null} value becomes an empty string; a
 * {@code null} name is refused.
 */
public record Parameters(Map<String, String> values) {

    public static final String ENTITY_ID = "entityID";
    public static final String REF_PERIOD = "refPeriod";
    public static final String BASE_CURRENCY = "baseCurrency";
    public static final String DECIMALS_MONETARY = "decimalsMonetary";
    public static final String DECIMALS_INTEGER = "decimalsInteger";

    public Parameters {
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((name, value) -> copy.put(Objects.requireNonNull(name, "name"), value == null ? "" : value));
        values = Collections.unmodifiableMap(copy);
    }

    /** Minimal parameters of a register of information: entity identifier, reference date, currency. */
    public static Parameters registerOfInformation(String entityId, LocalDate referenceDate, String currency) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(ENTITY_ID, Objects.requireNonNull(entityId, "entityId"));
        v.put(REF_PERIOD, Objects.requireNonNull(referenceDate, "referenceDate").toString());
        v.put(BASE_CURRENCY, Objects.requireNonNull(currency, "currency"));
        return new Parameters(v);
    }

    /** Returns a copy with the parameter added, or replaced if the name is already present. */
    public Parameters with(String name, String value) {
        Map<String, String> v = new LinkedHashMap<>(values);
        v.put(name, value);
        return new Parameters(v);
    }

    /** The value of the parameter, or {@code null} if absent. */
    public String value(String name) {
        return values.get(name);
    }
}

package eu.registredora.xbrlcsv;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Report parameters, written to {@code parameters.csv} (columns {@code name,value}).
 *
 * <p>The exact names and formats (entity identifier, decimals) are set by the EBA filing rules for the taxonomy
 * version in use; the library passes them through unchanged.
 */
public record Parameters(Map<String, String> values) {

    public static final String ENTITY_ID = "entityID";
    public static final String REF_PERIOD = "refPeriod";
    public static final String BASE_CURRENCY = "baseCurrency";
    public static final String DECIMALS_MONETARY = "decimalsMonetary";
    public static final String DECIMALS_INTEGER = "decimalsInteger";

    public Parameters {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Minimal parameters of a register of information: entity identifier, reference date, currency. */
    public static Parameters registerOfInformation(String entityId, LocalDate referenceDate, String currency) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(ENTITY_ID, Objects.requireNonNull(entityId, "entityId"));
        v.put(REF_PERIOD, Objects.requireNonNull(referenceDate, "referenceDate").toString());
        v.put(BASE_CURRENCY, Objects.requireNonNull(currency, "currency"));
        return new Parameters(v);
    }

    public Parameters with(String name, String value) {
        Map<String, String> v = new LinkedHashMap<>(values);
        v.put(name, value);
        return new Parameters(v);
    }

    public String value(String name) {
        return values.get(name);
    }
}

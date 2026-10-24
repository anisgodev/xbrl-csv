package tech.anisgodhbane.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParametersTest {

    @Test
    void turns_a_null_value_into_an_empty_string() {
        Parameters parameters = new Parameters(Map.of()).with(Parameters.DECIMALS_MONETARY, null);

        assertThat(parameters.value(Parameters.DECIMALS_MONETARY)).isEmpty();
    }

    @Test
    void refuses_a_null_name() {
        Map<String, String> values = new HashMap<>();
        values.put(null, "x");

        assertThatThrownBy(() -> new Parameters(values))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }
}

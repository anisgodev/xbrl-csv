package tech.anisgodhbane.xbrlcsv;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeiTest {

    @ParameterizedTest
    @ValueSource(strings = {"506700GE1G29325QX363", "529900T8BM49AURSDO55", "5493001KJTIIGC8Y1R12"})
    void accepts_real_leis(String lei) {
        assertThat(Lei.isValid(lei)).isTrue();
        assertThat(Lei.of(lei).value()).isEqualTo(lei);
    }

    @ParameterizedTest
    @ValueSource(strings = {"506700GE1G29325QX364", "506700GE1G29325QX36", "506700ge1g29325qx363x", "", "ABCDEFGHIJKLMNOPQRST"})
    void rejects_invalid_leis(String lei) {
        assertThat(Lei.isValid(lei)).isFalse();
    }

    @Test
    void normalises_case_and_whitespace() {
        assertThat(Lei.of(" 506700ge1g29325qx363 ").value()).isEqualTo("506700GE1G29325QX363");
    }

    @Test
    void normalises_case_whatever_the_default_locale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(Lei.of("5493001kjtiigc8y1r12").value()).isEqualTo("5493001KJTIIGC8Y1R12");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejects_null_and_wrong_lei_on_construction() {
        assertThat(Lei.isValid(null)).isFalse();
        assertThatThrownBy(() -> Lei.of("506700GE1G29325QX364")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computes_the_check_digits() {
        assertThat(Lei.checkDigits("506700GE1G29325QX3")).isEqualTo("63");
        assertThat(Lei.checkDigits("5493001KJTIIGC8Y1R")).isEqualTo("12");
    }
}

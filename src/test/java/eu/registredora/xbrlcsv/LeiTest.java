package eu.registredora.xbrlcsv;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeiTest {

    @ParameterizedTest
    @ValueSource(strings = {"506700GE1G29325QX363", "529900T8BM49AURSDO55", "5493001KJTIIGC8Y1R12"})
    void accepte_des_lei_reels(String lei) {
        assertThat(Lei.estValide(lei)).isTrue();
        assertThat(Lei.of(lei).valeur()).isEqualTo(lei);
    }

    @ParameterizedTest
    @ValueSource(strings = {"506700GE1G29325QX364", "506700GE1G29325QX36", "506700ge1g29325qx363x", "", "ABCDEFGHIJKLMNOPQRST"})
    void refuse_les_lei_invalides(String lei) {
        assertThat(Lei.estValide(lei)).isFalse();
    }

    @Test
    void normalise_la_casse_et_les_espaces() {
        assertThat(Lei.of(" 506700ge1g29325qx363 ").valeur()).isEqualTo("506700GE1G29325QX363");
    }

    @Test
    void refuse_null_et_lei_faux_a_la_construction() {
        assertThat(Lei.estValide(null)).isFalse();
        assertThatThrownBy(() -> Lei.of("506700GE1G29325QX364")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calcule_la_cle_de_controle() {
        assertThat(Lei.cleDeControle("506700GE1G29325QX3")).isEqualTo("63");
        assertThat(Lei.cleDeControle("5493001KJTIIGC8Y1R")).isEqualTo("12");
    }
}

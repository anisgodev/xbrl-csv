package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest {

    @Test
    void escapes_commas_quotes_and_line_breaks() {
        String csv = Csv.write(List.of("a", "b"), List.of(List.of("x,y", "says \"yes\""), List.of("line\nnext", "")));
        assertThat(csv).isEqualTo("a,b\r\n\"x,y\",\"says \"\"yes\"\"\"\r\n\"line\nnext\",\r\n");
    }

    @Test
    void reads_back_what_it_writes() {
        List<List<String>> rows = List.of(List.of("x,y", "says \"yes\""), List.of("é à ü", ""));
        String csv = Csv.write(List.of("a", "b"), rows);
        List<List<String>> read = Csv.read(csv);
        assertThat(read).containsExactly(List.of("a", "b"), rows.get(0), rows.get(1));
    }

    @Test
    void accepts_lf_only_and_bom() {
        assertThat(Csv.read("\uFEFFa,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void quotes_a_row_made_of_a_single_empty_value() {
        assertThat(Csv.write(List.of("a"), List.of(List.of(""), List.of("x")))).isEqualTo("a\r\n\"\"\r\nx\r\n");
    }

    @Test
    void keeps_a_quoted_empty_row_and_ignores_blank_lines() {
        assertThat(Csv.read("a\r\n\"\"\r\n\r\nx\r\n\r\n\"\"\r\n\r\n"))
                .containsExactly(List.of("a"), List.of(""), List.of("x"), List.of(""));
    }

    @Test
    void rejects_more_values_than_allowed() {
        assertThatThrownBy(() -> Csv.read("a,b\r\n1,2\r\n", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many values");
    }

    @Test
    void accepts_exactly_the_allowed_number_of_values() {
        assertThat(Csv.read("a,b\r\n1,2\r\n", 4)).hasSize(2);
    }

    @Test
    void rejects_an_unclosed_quote() {
        assertThatThrownBy(() -> Csv.read("a\n\"x")).isInstanceOf(IllegalArgumentException.class);
    }
}

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
        assertThat(Csv.read("﻿a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void rejects_an_unclosed_quote() {
        assertThatThrownBy(() -> Csv.read("a\n\"x")).isInstanceOf(IllegalArgumentException.class);
    }
}

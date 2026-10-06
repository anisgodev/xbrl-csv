package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest {

    @Test
    void echappe_virgules_guillemets_et_retours() {
        String csv = Csv.ecrire(List.of("a", "b"), List.of(List.of("x,y", "dit \"oui\""), List.of("ligne\nsuivante", "")));
        assertThat(csv).isEqualTo("a,b\r\n\"x,y\",\"dit \"\"oui\"\"\"\r\n\"ligne\nsuivante\",\r\n");
    }

    @Test
    void relit_ce_qu_il_ecrit() {
        List<List<String>> lignes = List.of(List.of("x,y", "dit \"oui\""), List.of("é à ü", ""));
        String csv = Csv.ecrire(List.of("a", "b"), lignes);
        List<List<String>> relu = Csv.lire(csv);
        assertThat(relu).containsExactly(List.of("a", "b"), lignes.get(0), lignes.get(1));
    }

    @Test
    void accepte_lf_seul_et_bom() {
        assertThat(Csv.lire("﻿a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void refuse_un_guillemet_non_ferme() {
        assertThatThrownBy(() -> Csv.lire("a\n\"x")).isInstanceOf(IllegalArgumentException.class);
    }
}

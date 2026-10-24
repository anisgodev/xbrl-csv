package tech.anisgodhbane.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableTest {

    @Test
    void the_builder_turns_null_values_into_empty_strings() {
        Table table = Table.builder("B_01.01", "c0010", "c0020")
                .row("a", null)
                .row(Arrays.asList(null, "b"))
                .build();

        assertThat(table.rows()).containsExactly(List.of("a", ""), List.of("", "b"));
    }
}

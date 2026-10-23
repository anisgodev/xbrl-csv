package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralValidatorTest {

    private final StructuralValidator validator = new StructuralValidator();

    @Test
    void the_rule_list_covers_every_emitted_code() throws IOException {
        String source = Files.readString(Path.of("src/main/java/eu/registredora/xbrlcsv/StructuralValidator.java"));
        List<String> emitted = java.util.regex.Pattern.compile("(?:blocking|warning)\\(\"(XC-[A-Z-]+)\"").matcher(source)
                .results().map(m -> m.group(1)).distinct().toList();
        assertThat(emitted).isNotEmpty();
        assertThat(StructuralValidator.RULES).containsExactlyInAnyOrderElementsOf(emitted);
    }

    @Test
    void a_correct_package_produces_no_message() {
        assertThat(validator.validate(ReportPackageFixtures.simplePackage())).isEmpty();
    }

    @Test
    void detects_undeclared_and_missing_tables() {
        Table b0101 = Table.builder("B_01.01", "c0010").row("x").build();
        ReportPackage reportPackage = new ReportPackage("p", "http://x", validParameters(),
                List.of(new FilingIndicator("B_02.01", true)), List.of(b0101));

        assertThat(codes(validator.validate(reportPackage)))
                .containsExactlyInAnyOrder("XC-TABLE-UNDECLARED", "XC-TABLE-MISSING");
    }

    @Test
    void detects_rows_of_wrong_width_and_duplicate_columns() {
        Table table = Table.builder("B_05.01", "c0010", "c0010").row("a").build();
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("p", "http://x", validParameters(), List.of(table));

        List<ValidationMessage> messages = validator.validate(reportPackage);
        assertThat(codes(messages)).containsExactlyInAnyOrder("XC-COLUMN-DUPLICATE", "XC-ROW-WIDTH");
        assertThat(messages).filteredOn(m -> m.code().equals("XC-ROW-WIDTH"))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.table()).isEqualTo("B_05.01");
                    assertThat(m.row()).isEqualTo(1);
                });
    }

    @Test
    void detects_missing_or_malformed_parameters() {
        Parameters parameters = new Parameters(Map.of("refPeriod", "31/12/2026", "baseCurrency", "euro"));
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("name with space", "http://x", parameters, List.of());

        assertThat(codes(validator.validate(reportPackage))).containsExactlyInAnyOrder(
                "XC-PACKAGE-NAME", "XC-PARAM-ENTITY", "XC-PARAM-PERIOD", "XC-PARAM-CURRENCY");
    }

    @Test
    void flags_an_empty_row_as_a_warning() {
        Table table = Table.builder("B_06.01", "c0010", "c0020").row("", " ").build();
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("p", "http://x", validParameters(), List.of(table));

        assertThat(validator.validate(reportPackage)).singleElement()
                .satisfies(m -> {
                    assertThat(m.code()).isEqualTo("XC-ROW-EMPTY");
                    assertThat(m.severity()).isEqualTo(ValidationMessage.Severity.WARNING);
                });
    }

    private static Parameters validParameters() {
        return Parameters.registerOfInformation("rs:X.CON", LocalDate.of(2026, 12, 31), "EUR");
    }

    private static List<String> codes(List<ValidationMessage> messages) {
        return messages.stream().map(ValidationMessage::code).toList();
    }
}

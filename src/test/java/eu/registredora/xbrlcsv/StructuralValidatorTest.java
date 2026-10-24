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

    @Test
    void rejects_an_impossible_reference_date() {
        Parameters parameters = Parameters.registerOfInformation("rs:X.CON", LocalDate.of(2026, 12, 31), "EUR")
                .with(Parameters.REF_PERIOD, "2026-13-45");
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("p", "http://x", parameters, List.of());

        assertThat(codes(validator.validate(reportPackage))).containsExactly("XC-PARAM-PERIOD");
    }

    @Test
    void accepts_a_prefixed_currency_and_rejects_a_lower_case_one() {
        ReportPackage prefixed = ReportPackage.withDerivedFilingIndicators("p", "http://x",
                validParameters().with(Parameters.BASE_CURRENCY, "iso4217:EUR"), List.of());
        ReportPackage lowerCase = ReportPackage.withDerivedFilingIndicators("p", "http://x",
                validParameters().with(Parameters.BASE_CURRENCY, "eur"), List.of());

        assertThat(validator.validate(prefixed)).isEmpty();
        assertThat(codes(validator.validate(lowerCase))).containsExactly("XC-PARAM-CURRENCY");
    }

    @Test
    void detects_a_blank_entry_point() {
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("p", " ", validParameters(), List.of());

        assertThat(codes(validator.validate(reportPackage))).containsExactly("XC-ENTRY-POINT");
    }

    @Test
    void detects_duplicate_indicators_and_tables() {
        Table table = Table.builder("B_01.01", "c0010").row("x").build();
        ReportPackage reportPackage = new ReportPackage("p", "http://x", validParameters(),
                List.of(new FilingIndicator("B_01.01", true), new FilingIndicator("B_01.01", true)),
                List.of(table, table));

        assertThat(codes(validator.validate(reportPackage)))
                .containsExactlyInAnyOrder("XC-INDICATOR-DUPLICATE", "XC-TABLE-DUPLICATE");
    }

    @Test
    void detects_a_malformed_code_and_tables_without_usable_columns() {
        Table badCode = Table.builder("B-01-01", "c0010").row("x").build();
        Table noColumns = Table.builder("B_02.01").build();
        Table blankColumn = Table.builder("B_03.01", "c0010", " ").row("x", "y").build();
        ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators("p", "http://x", validParameters(),
                List.of(badCode, noColumns, blankColumn));

        assertThat(codes(validator.validate(reportPackage)))
                .containsExactlyInAnyOrder("XC-TABLE-CODE", "XC-TABLE-COLUMNS", "XC-COLUMN-EMPTY");
    }

    @Test
    void says_when_a_present_table_is_declared_as_not_reported() {
        Table table = Table.builder("B_01.01", "c0010").row("x").build();
        ReportPackage reportPackage = new ReportPackage("p", "http://x", validParameters(),
                List.of(new FilingIndicator("B_01.01", false)), List.of(table));

        assertThat(validator.validate(reportPackage)).singleElement()
                .satisfies(m -> {
                    assertThat(m.code()).isEqualTo("XC-TABLE-UNDECLARED");
                    assertThat(m.text()).contains("reported = false");
                });
    }

    @Test
    void reports_missing_tables_in_declaration_order() {
        List<String> declared = List.of("B_09.01", "B_01.01", "B_05.02", "B_02.03", "B_07.01", "B_03.02");
        ReportPackage reportPackage = new ReportPackage("p", "http://x", validParameters(),
                declared.stream().map(t -> new FilingIndicator(t, true)).toList(), List.of());

        assertThat(validator.validate(reportPackage)).extracting(ValidationMessage::table)
                .containsExactlyElementsOf(declared);
    }

    private static Parameters validParameters() {
        return Parameters.registerOfInformation("rs:X.CON", LocalDate.of(2026, 12, 31), "EUR");
    }

    private static List<String> codes(List<ValidationMessage> messages) {
        return messages.stream().map(ValidationMessage::code).toList();
    }
}

# Changelog

## Unreleased

### Fixed

- `XbrlCsvWriter` output no longer depends on the JVM's time zone: the same package gives the same bytes and hash on
  every machine. Archives written by 0.1.0 differ in their entry timestamps only.
- Reading a package no longer drops the empty rows of a single-column table: the writer quotes a lone empty value
  (`""`) and the reader keeps it, while still ignoring blank lines.
- `Table.Builder.row(String...)` accepts `null` values and turns them into empty strings, as documented.
- `Lei.of` upper-cases with `Locale.ROOT`, so it works under a Turkish default locale.
- `XC-PARAM-PERIOD` rejects impossible dates such as `2026-13-45`.

### Changed

- `StructuralValidator` returns messages in a stable order; `XC-TABLE-MISSING` follows the declaration order.
- `XC-TABLE-UNDECLARED` says when the table is declared with `reported = false` rather than not declared.
- `XbrlCsvWriter` refuses a package name that is not a single folder name, and two tables written to the same file.
- `XbrlCsvReader` rejects duplicate entries, several `reports/report.json`, and a `reported` value other than `true`
  or `false` (previously read as `false`).
- The jar declares `Automatic-Module-Name: eu.registredora.xbrlcsv`; `mvn verify` writes a JaCoCo coverage report; CI
  also runs on Java 25.

## 0.1.0 (2026-10-24)

First release on Maven Central as `tech.anisgodhbane:xbrl-csv`.

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
- `XbrlCsvWriter` no longer fails with a `NullPointerException` on a parameter whose value is `null`: `Parameters`
  turns a `null` value into an empty string, as `Table` does, and refuses a `null` name.
- `XbrlCsvWriter` refuses a table code that does not give a plain file name (`../x`, `B_01/01`, `parameters`...).
  Such codes produced entries outside `reports/`, or tables that disappeared when the package was read back.
- `XbrlCsvReader` caps the number of CSV values in a package, 5 million by default, settable with
  `new XbrlCsvReader(maxValues)`. A small archive of commas used to expand into hundreds of millions of values; the
  parsed package now stays within about 250 MB of heap at the default cap.
- A table whose code is not upper case (`b_01.01`) survives a write and read: the reader takes the code spelled by the
  matching filing indicator instead of upper-casing the file name, which made the package fail its own validation.
- When a package exceeds the value cap, the message gives the cap (`more than 5000000 in all`), not what was left of
  it when the last file was read.
- `XbrlCsvWriter` refuses a table without columns, which used to vanish when the package was read back.
- `XbrlCsvReader` rejects a `reports/` folder deeper than the root folder (`a/b/reports/`), which was read under the
  name `a` and moved when written again, and a parameter named twice in `parameters.csv` (the last one used to win).

### Changed

- `StructuralValidator` warns with the new rule `XC-PARAM-LEI` when `entityID` does not carry a valid LEI, and with
  `XC-PARAM-CURRENCY` when `baseCurrency` is missing.
- `StructuralValidator` returns messages in a stable order; `XC-TABLE-MISSING` follows the declaration order.
- `XC-TABLE-UNDECLARED` says when the table is declared with `reported = false` rather than not declared.
- `XbrlCsvWriter` refuses a package name that is not a single folder name, and two tables written to the same file.
- `XbrlCsvWriter.write(pkg, out)` and `XbrlCsvReader.read(in)` leave the caller's stream open; they used to close it.
- `XbrlCsvReader` returns tables in the order of the filing indicators, then the undeclared ones by file name (was:
  by file name), so a package read back equals the one written.
- `XbrlCsvReader` rejects a `parameters.csv` or `FilingIndicators.csv` whose header is not `name,value` or
  `templateID,reported`, or with a row of other than two values (such rows used to be skipped).
- `XbrlCsvReader` rejects duplicate entries, several `reports/report.json`, and a `reported` value other than `true`
  or `false` (previously read as `false`).
- The jar declares `Automatic-Module-Name: eu.registredora.xbrlcsv`; `mvn verify` writes a JaCoCo coverage report; CI
  also runs on Java 25 and builds the Javadoc; `mvn verify` fails below 90% instruction or 80% branch coverage.

## 0.1.0 (2026-10-24)

First release on Maven Central as `tech.anisgodhbane:xbrl-csv`.

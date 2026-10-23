# xBRL-CSV

Standalone Java library (no Spring) to write, read and check xBRL-CSV report packages, in particular the DORA
register of information filed with the authorities (EBA DORA taxonomy).

Only dependency: Jackson. Java 21 or later.

| Need | Class |
|---|---|
| Describe a package: tables, parameters, filing indicators | `ReportPackage`, `Table`, `Parameters`, `FilingIndicator` |
| Produce the `.zip` to file, byte-identical on every run | `XbrlCsvWriter` |
| Read back an existing `.zip`, malicious archives rejected | `XbrlCsvReader` |
| Check the structure before filing | `StructuralValidator`, `ValidationMessage` |
| Validate a LEI (ISO 17442, ISO 7064 MOD 97-10 check digits) | `Lei` |

The taxonomy's own checks (EBA rules) are not here: the platform delegates them to Arelle.

## Quick start

```java
import eu.registredora.xbrlcsv.*;

import java.time.LocalDate;
import java.util.List;

Table b0101 = Table.builder("B_01.01", "c0010", "c0020", "c0030")
        .row("506700GE1G29325QX363", "Example Insurance SA", "BE")
        .build();
ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators(
        "506700GE1G29325QX363_2026-12-31",
        "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.0/mod/dora.json",
        Parameters.registerOfInformation("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR"),
        List.of(b0101));

List<ValidationMessage> issues = new StructuralValidator().validate(reportPackage);
byte[] zip = new XbrlCsvWriter().write(reportPackage);
ReportPackage readBack = new XbrlCsvReader().read(zip);
```

To use it in another project, install it in the local Maven repository (see [Build](#build)) and declare:

```xml
<dependency>
    <groupId>eu.registredora</groupId>
    <artifactId>xbrl-csv</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Features

### Package model

All model classes are immutable `record`s: lists and value maps passed as arguments are copied.

**`Table`**: a report table, identified by its code (`B_01.01`), with its column codes and its rows. A `null` value
becomes an empty string.

```java
Table b0501 = Table.builder("B_05.01", List.of("c0010", "c0020", "c0050"))
        .row("5493001KJTIIGC8Y1R12", "LEI", "Cloud, \"Nimbus\" SA")
        .row(List.of("529900T8BM49AURSDO55", "LEI", "Other provider"))
        .build();

b0501.fileName();                        // "b_05.01.csv"
b0501.columnIndex("c0050");              // 2 (-1 if absent)
Table.codeFromFileName("b_05.01.csv");   // "B_05.01"
```

**`Parameters`**: the content of `parameters.csv`, in insertion order. `registerOfInformation(...)` fills the three
minimal parameters of a register of information (`entityID`, `refPeriod` in `YYYY-MM-DD` format, `baseCurrency`);
`with(...)` returns an enriched copy. Common names are constants (`ENTITY_ID`, `REF_PERIOD`, `BASE_CURRENCY`,
`DECIMALS_MONETARY`, `DECIMALS_INTEGER`). Values are passed through unchanged: their exact format is governed by the
EBA filing rules for the taxonomy version in use.

```java
Parameters parameters = Parameters.registerOfInformation("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR")
        .with(Parameters.DECIMALS_MONETARY, "-3");
parameters.value(Parameters.REF_PERIOD);   // "2026-12-31"
```

**`FilingIndicator`**: a row of `FilingIndicators.csv`, a template code and whether it is reported.

**`ReportPackage`**: the complete package, with its name (root folder of the archive), the URL of the taxonomy entry
point, the parameters, the filing indicators and the tables. Two ways to build it:

```java
// Derived indicators: every table supplied is reported (reported = true)
ReportPackage reportPackage = ReportPackage.withDerivedFilingIndicators(name, entryPoint, parameters, List.of(b0101, b0501));

// Explicit indicators, for example to declare a template that is not filled in
ReportPackage explicit = new ReportPackage(name, entryPoint, parameters,
        List.of(new FilingIndicator("B_01.01", true), new FilingIndicator("B_02.01", false)),
        List.of(b0101));

reportPackage.table("B_05.01");   // Optional<Table>
```

### Writing a package: `XbrlCsvWriter`

`write(reportPackage)` returns the bytes of the `.zip`; `write(reportPackage, out)` writes to an `OutputStream`. The
archive follows the xBRL Report Package 2023 and xBRL-CSV specifications:

```
{name}/META-INF/reportPackage.json      documentType https://xbrl.org/report-package/2023
{name}/reports/report.json              documentType https://xbrl.org/2021/xbrl-csv, extends [entry point]
{name}/reports/parameters.csv           name,value
{name}/reports/FilingIndicators.csv     templateID,reported
{name}/reports/b_01.01.csv ...          one file per table, lower-case name
```

- RFC 4180 CSV: comma separator, CRLF line endings, double quotes around values containing a comma, a quote or a
  line break (`Cloud, "Nimbus" SA` becomes `"Cloud, ""Nimbus"" SA"`).
- UTF-8 files without BOM.
- Deterministic output: fixed entry order and timestamps, so the same package always produces the same bytes and the
  same hash, which lets you compare two runs or prove what was filed.

```java
byte[] zip = new XbrlCsvWriter().write(reportPackage);
String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));

try (OutputStream out = Files.newOutputStream(Path.of(reportPackage.name() + ".zip"))) {
    new XbrlCsvWriter().write(reportPackage, out);
}
```

### Reading a package: `XbrlCsvReader`

`read(byte[])` or `read(InputStream)` rebuilds a `ReportPackage`, for example from a client's filing of the previous
year. A package written then read back gives the same name, entry point, parameters, filing indicators and tables.

- The package is located by its `reports/report.json`; the entry point comes from `documentInfo.extends`.
- Every other `.csv` in `reports/` becomes a `Table` whose code is derived from the file name; the first row is the
  column header.
- Lenient on input: UTF-8 BOM and bare LF line endings accepted, trailing empty lines ignored.

Malicious or broken archives are rejected with an `IllegalArgumentException`:

| Case | Message |
|---|---|
| Absolute path or path climbing out (`../`) | `Invalid package: forbidden path ...` |
| More than 500 entries | `Invalid package: too many entries` |
| More than 200 MB uncompressed | `Invalid package: uncompressed size too large` |
| Not a ZIP, or empty ZIP | `Invalid package: empty or non-ZIP archive` |
| `reports/report.json` missing | `Invalid package: reports/report.json missing` |
| `report.json` unreadable or without `documentInfo.extends` | `Invalid package: ...` |
| Unclosed quote in a CSV | `Invalid CSV: unclosed quote` |

An I/O error on the input stream is raised as an `UncheckedIOException`.

### Structural checks: `StructuralValidator`

`validate(reportPackage)` returns the list of issues, empty if the package is correct. These checks do not depend on
the taxonomy: they catch what would get the package rejected before its content is even read.

Each `ValidationMessage` carries a severity (`BLOCKING` prevents filing, `WARNING` should be reviewed), a stable rule
code, and when relevant the table, the data row number (1 = first row after the header) and the column concerned,
plus a human-readable text.

```java
List<ValidationMessage> messages = new StructuralValidator().validate(reportPackage);
boolean fileable = messages.stream().noneMatch(m -> m.severity() == ValidationMessage.Severity.BLOCKING);
messages.forEach(m -> System.out.printf("%s %s %s row %s: %s%n",
        m.severity(), m.code(), m.table(), m.row(), m.text()));
```

| Code | Severity | Check |
|---|---|---|
| `XC-PACKAGE-NAME` | blocking | Package name limited to letters, digits, `.`, `-` and `_` |
| `XC-ENTRY-POINT` | blocking | Taxonomy entry point not empty |
| `XC-PARAM-ENTITY` | blocking | `entityID` parameter present |
| `XC-PARAM-PERIOD` | blocking | `refPeriod` parameter present, in `YYYY-MM-DD` format |
| `XC-PARAM-CURRENCY` | blocking | `baseCurrency`, if supplied, is an ISO 4217 code (`EUR` or `iso4217:EUR`) |
| `XC-INDICATOR-DUPLICATE` | blocking | No two filing indicators for the same template |
| `XC-TABLE-DUPLICATE` | blocking | No two tables with the same code |
| `XC-TABLE-UNDECLARED` | blocking | Every table present is declared in `FilingIndicators.csv` |
| `XC-TABLE-MISSING` | blocking | Every declared template is present in the package |
| `XC-TABLE-CODE` | blocking | Table code of the form `B_01.01` |
| `XC-TABLE-COLUMNS` | blocking | The table has at least one column |
| `XC-COLUMN-EMPTY` | blocking | No empty column name |
| `XC-COLUMN-DUPLICATE` | blocking | No two columns with the same code |
| `XC-ROW-WIDTH` | blocking | Every row has as many values as there are columns |
| `XC-ROW-EMPTY` | warning | Entirely empty row |

The list of codes is exposed by `StructuralValidator.RULES`, for example to report how many rules a check has
verified; a test guarantees it covers every code emitted.

### LEI identifiers: `Lei`

A LEI (ISO 17442) has 18 upper-case alphanumeric characters followed by 2 check digits (ISO 7064 MOD 97-10).

```java
Lei.isValid("506700GE1G29325QX363");       // true
Lei.isValid("506700GE1G29325QX364");       // false: wrong check digits
Lei.of(" 506700ge1g29325qx363 ").value();  // "506700GE1G29325QX363": whitespace stripped, upper-cased
Lei.of("506700GE1G29325QX364");            // IllegalArgumentException
Lei.checkDigits("506700GE1G29325QX3");     // "63"
```

`Lei.of` normalises before validating; the constructor `new Lei(...)` requires an already normalised value.

## Build

```bash
mvn verify      # compiles and runs the tests
mvn install     # installs eu.registredora:xbrl-csv:0.1.0-SNAPSHOT in the local Maven repository
```

CI (GitHub Actions, Java 21 Temurin) runs `mvn -B verify` and checks the sync script's syntax on every push to
`main` and on every pull request.

## Where this code comes from

The library is developed in the platform repository, `anisgodev/Registre_information`, module
`registre-dora/xbrl-csv`, which remains the source. This repository is a standalone copy of it: same git history
(extracted with `git subtree split`), same code, and a `pom.xml` without a parent. The source commit of the last sync
is recorded in `PLATFORM-SOURCE`.

| Case | Command (from this repository) |
|---|---|
| After a change to `xbrl-csv` in the platform | `scripts/sync.sh <path to Registre_information>`, then `mvn verify` and commit `src/`, `pom.xml`, `PLATFORM-SOURCE` |
| Check whether this repository is up to date | `scripts/sync.sh --check <path to Registre_information>` |
| Change made here first | `scripts/sync.sh --to-platform <path to Registre_information>` copies `src/` into the platform (a change to the `pom.xml` dependencies is reported, to be applied by hand); commit there, then sync normally here |

The only allowed difference between the two is the `pom.xml`: the script replaces the `registre-dora` parent with the
version, Java properties and plugin versions read from the platform's parent pom, and keeps this repository's `<url>`
and `<licenses>`.

The sandbox (`anisgodev/registre-bac-a-sable`) keeps its own copy, synced from the platform by its
`scripts/synchroniser-moteur.sh`.

## Support the project

xBRL-CSV is free and open source. If it saves you time, you can support its development through
[GitHub Sponsors](https://github.com/sponsors/anisgodev) (the "Sponsor" button at the top of the repository). Companies
that rely on it for their DORA filings are especially welcome to sponsor.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify and redistribute this library, including
in commercial and closed-source products, provided you keep the copyright and license notices.

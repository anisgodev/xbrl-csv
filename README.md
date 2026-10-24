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

The library is published on Maven Central:

```xml
<dependency>
    <groupId>tech.anisgodhbane</groupId>
    <artifactId>xbrl-csv</artifactId>
    <version>0.1.0</version>
</dependency>
```

The Java packages stay `eu.registredora.xbrlcsv`; only the Maven coordinates use `tech.anisgodhbane`.

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
EBA filing rules for the taxonomy version in use. A `null` value becomes an empty string; a `null` name is refused.

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

`write(reportPackage)` returns the bytes of the `.zip`; `write(reportPackage, out)` writes to an `OutputStream`, which
it leaves open. The archive follows the xBRL Report Package 2023 and xBRL-CSV specifications:

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
- RFC 4180 CSV: a row made of a single empty value is written `""`, so it is not mistaken for a blank line.
- Deterministic output: fixed entry order and timestamps, so the same package always produces the same bytes and the
  same hash on any machine and in any time zone, which lets you compare two runs or prove what was filed.

The writer refuses, with an `IllegalArgumentException`, a package name that is not a single folder name (empty, `.`,
`..`, or containing `/` or `\`), a table code that does not give a plain file name (empty, `.`, `..`, containing `/` or
`\`, or named like `parameters.csv` or `FilingIndicators.csv`) and two tables whose codes give the same file name
(`B_01.01` and `b_01.01`). The other
structural problems are reported by `StructuralValidator`, which should run before writing.

```java
byte[] zip = new XbrlCsvWriter().write(reportPackage);
String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));

try (OutputStream out = Files.newOutputStream(Path.of(reportPackage.name() + ".zip"))) {
    new XbrlCsvWriter().write(reportPackage, out);
}
```

### Reading a package: `XbrlCsvReader`

`read(byte[])` or `read(InputStream)` rebuilds a `ReportPackage`, for example from a client's filing of the previous
year. A package written then read back is equal to the original. `read(InputStream)` leaves the stream open.

- The package is located by its `reports/report.json`; the entry point comes from `documentInfo.extends`.
- `parameters.csv` must have the header `name,value` and `FilingIndicators.csv` the header `templateID,reported`
  (case ignored), with exactly two values on every row.
- Every other `.csv` in `reports/` becomes a `Table` whose code is derived from the file name; the first row is the
  column header. Tables come in the order of the filing indicators, then the undeclared ones by file name.
- The archive is held in memory while it is read: allow a few times its uncompressed size in heap.
- Lenient on input: UTF-8 BOM and bare LF line endings accepted, blank lines ignored (a quoted empty value `""` on
  its own line is kept as a row).

Malicious or broken archives are rejected with an `IllegalArgumentException`:

| Case | Message |
|---|---|
| Absolute path or path climbing out (`../`) | `Invalid package: forbidden path ...` |
| More than 500 entries | `Invalid package: too many entries` |
| More than 200 MB uncompressed | `Invalid package: uncompressed size too large` |
| Not a ZIP, or empty ZIP | `Invalid package: empty or non-ZIP archive` |
| Two entries with the same path | `Invalid package: duplicate entry ...` |
| `reports/report.json` missing | `Invalid package: reports/report.json missing` |
| More than one `reports/report.json` | `Invalid package: several reports/report.json ...` |
| More than 10 million CSV values in all | `Invalid CSV: too many values ...` |
| `parameters.csv` or `FilingIndicators.csv` with another header, or a row without exactly two values | `Invalid package: parameters.csv ...` |
| `reported` other than `true` or `false` in `FilingIndicators.csv` | `Invalid package: reported must be true or false ...` |
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
| `XC-PARAM-PERIOD` | blocking | `refPeriod` parameter present, a real date in `YYYY-MM-DD` format |
| `XC-PARAM-CURRENCY` | blocking | `baseCurrency`, if supplied, is an ISO 4217 code (`EUR` or `iso4217:EUR`) |
| `XC-INDICATOR-DUPLICATE` | blocking | No two filing indicators for the same template |
| `XC-TABLE-DUPLICATE` | blocking | No two tables with the same code |
| `XC-TABLE-UNDECLARED` | blocking | Every table present is declared in `FilingIndicators.csv` with `reported = true` |
| `XC-TABLE-MISSING` | blocking | Every declared template is present in the package |
| `XC-TABLE-CODE` | blocking | Table code of the form `B_01.01` |
| `XC-TABLE-COLUMNS` | blocking | The table has at least one column |
| `XC-COLUMN-EMPTY` | blocking | No empty column name |
| `XC-COLUMN-DUPLICATE` | blocking | No two columns with the same code |
| `XC-ROW-WIDTH` | blocking | Every row has as many values as there are columns |
| `XC-ROW-EMPTY` | warning | Entirely empty row |

Messages come in a stable order: package, parameters, filing indicators, then each table in the order supplied.

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

`Lei.of` normalises before validating, whatever the default locale; the constructor `new Lei(...)` requires an already normalised value.

## Build

```bash
mvn verify      # compiles, runs the tests and writes the coverage report to target/site/jacoco/index.html
mvn install     # installs tech.anisgodhbane:xbrl-csv:0.1.0-SNAPSHOT in the local Maven repository
```

CI (GitHub Actions, Temurin Java 21 and 25) runs `mvn -B verify` on every push to `main` and on every pull request.
The jar declares the module name `eu.registredora.xbrlcsv` (`Automatic-Module-Name`). Changes are listed in
[CHANGELOG.md](CHANGELOG.md).

## Release

Pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`: it sets the version to `X.Y.Z`, builds and tests,
attaches the sources and javadoc jars, signs everything with GPG, publishes to Maven Central (Sonatype Central
Portal) and creates the GitHub Release with the jars.

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The workflow needs four repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `CENTRAL_TOKEN_USERNAME` | username of a user token generated at central.sonatype.com > View Account > Generate User Token |
| `CENTRAL_TOKEN_PASSWORD` | password of that user token |
| `GPG_PRIVATE_KEY` | ASCII-armoured private key: `gpg --armor --export-secret-keys <KEY_ID>` |
| `GPG_PASSPHRASE` | passphrase of that key |

The public key must be on a key server Central checks, e.g. `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`.

To publish from a workstation instead: put the token in `~/.m2/settings.xml` under a `<server>` with id `central`,
then run `mvn -Prelease -Dgpg.signer=gpg deploy`.

## Where this code comes from

This repository is the source of the library. It was first developed in the platform repository,
`anisgodev/Registre_information` (module `registre-dora/xbrl-csv`, history kept here with `git subtree split`); the
platform and the sandbox (`anisgodev/registre-bac-a-sable`) now use the version published on Maven Central. To ship a
change to them, release a new version here, then bump the `xbrl-csv.version` property in the platform's
`registre-dora/pom.xml` (the sandbox aligns on it with its `scripts/synchroniser-moteur.sh`).

## Support the project

xBRL-CSV is free and open source. If it saves you time, you can support its development through
[Liberapay](https://liberapay.com/anisgodev) (the "Sponsor" button at the top of the repository). Companies that rely
on it for their DORA filings are especially welcome to contribute.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify and redistribute this library, including
in commercial and closed-source products, provided you keep the copyright and license notices.

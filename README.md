# xBRL-CSV

Bibliothèque Java autonome (sans Spring) pour écrire, lire et contrôler des paquets de rapport xBRL-CSV, en
particulier le registre d'information DORA déposé auprès des autorités (taxonomie DORA de l'EBA).

Seule dépendance : Jackson. Java 21 ou plus.

| Besoin | Classe |
|---|---|
| Décrire un paquet : tableaux, paramètres, indicateurs de dépôt | `PaquetDeRapport`, `Table`, `Parametres`, `IndicateurDeDepot` |
| Produire le `.zip` à déposer, octets identiques à chaque génération | `XbrlCsvWriter` |
| Relire un `.zip` existant, archives malveillantes refusées | `XbrlCsvReader` |
| Contrôler la structure avant dépôt | `ValidateurStructurel`, `MessageDeValidation` |
| Valider un LEI (ISO 17442, clé ISO 7064 MOD 97-10) | `Lei` |

Les contrôles de la taxonomie elle-même (règles EBA) ne sont pas ici : la plateforme les confie à Arelle.

## Démarrage rapide

```java
import eu.registredora.xbrlcsv.*;

import java.time.LocalDate;
import java.util.List;

Table b0101 = Table.builder("B_01.01", "c0010", "c0020", "c0030")
        .ligne("506700GE1G29325QX363", "Assurance Exemple SA", "BE")
        .build();
PaquetDeRapport paquet = PaquetDeRapport.avecIndicateursDeduits(
        "506700GE1G29325QX363_2026-12-31",
        "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.0/mod/dora.json",
        Parametres.registre("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR"),
        List.of(b0101));

List<MessageDeValidation> anomalies = new ValidateurStructurel().valider(paquet);
byte[] zip = new XbrlCsvWriter().ecrire(paquet);
PaquetDeRapport relu = new XbrlCsvReader().lire(zip);
```

Pour l'utiliser dans un autre projet, installez-la dans le dépôt Maven local (voir [Construire](#construire)) puis
déclarez :

```xml
<dependency>
    <groupId>eu.registredora</groupId>
    <artifactId>xbrl-csv</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Fonctionnalités

### Modèle d'un paquet

Toutes les classes du modèle sont des `record` immuables : les listes et les tables de valeurs passées en argument
sont copiées.

**`Table`** : un tableau du rapport, identifié par son code (`B_01.01`), avec ses codes de colonnes et ses lignes.
Une valeur `null` devient une chaîne vide.

```java
Table b0501 = Table.builder("B_05.01", List.of("c0010", "c0020", "c0050"))
        .ligne("5493001KJTIIGC8Y1R12", "LEI", "Nuage, \"Cloud\" SA")
        .ligne(List.of("529900T8BM49AURSDO55", "LEI", "Autre prestataire"))
        .build();

b0501.nomFichier();                       // "b_05.01.csv"
b0501.indexColonne("c0050");              // 2 (-1 si absente)
Table.codeDepuisFichier("b_05.01.csv");   // "B_05.01"
```

**`Parametres`** : le contenu de `parameters.csv`, dans l'ordre d'insertion. `registre(...)` remplit les trois
paramètres minimaux d'un registre d'information (`entityID`, `refPeriod` au format `AAAA-MM-JJ`, `baseCurrency`) ;
`avec(...)` renvoie une copie enrichie. Les noms usuels sont des constantes (`ENTITY_ID`, `REF_PERIOD`,
`BASE_CURRENCY`, `DECIMALS_MONETARY`, `DECIMALS_INTEGER`). Les valeurs sont transmises telles quelles : leur format
exact relève des règles de dépôt de l'EBA pour la version de taxonomie utilisée.

```java
Parametres parametres = Parametres.registre("rs:506700GE1G29325QX363.CON", LocalDate.of(2026, 12, 31), "EUR")
        .avec(Parametres.DECIMALS_MONETARY, "-3");
parametres.valeur(Parametres.REF_PERIOD);   // "2026-12-31"
```

**`IndicateurDeDepot`** : une ligne de `FilingIndicators.csv`, un code de modèle et s'il est déclaré.

**`PaquetDeRapport`** : le paquet complet, avec son nom (dossier racine de l'archive), l'URL du point d'entrée de la
taxonomie, les paramètres, les indicateurs et les tableaux. Deux façons de le construire :

```java
// Indicateurs déduits : chaque tableau fourni est déclaré (reported = true)
PaquetDeRapport paquet = PaquetDeRapport.avecIndicateursDeduits(nom, pointEntree, parametres, List.of(b0101, b0501));

// Indicateurs explicites, par exemple pour déclarer un modèle non rempli
PaquetDeRapport explicite = new PaquetDeRapport(nom, pointEntree, parametres,
        List.of(new IndicateurDeDepot("B_01.01", true), new IndicateurDeDepot("B_02.01", false)),
        List.of(b0101));

paquet.table("B_05.01");   // Optional<Table>
```

### Écriture du paquet : `XbrlCsvWriter`

`ecrire(paquet)` renvoie les octets du `.zip` ; `ecrire(paquet, sortie)` écrit dans un `OutputStream`. L'archive
suit la spécification xBRL Report Package 2023 et xBRL-CSV :

```
{nom}/META-INF/reportPackage.json      documentType https://xbrl.org/report-package/2023
{nom}/reports/report.json              documentType https://xbrl.org/2021/xbrl-csv, extends [point d'entrée]
{nom}/reports/parameters.csv           name,value
{nom}/reports/FilingIndicators.csv     templateID,reported
{nom}/reports/b_01.01.csv ...          un fichier par tableau, nom en minuscules
```

- CSV conformes à la RFC 4180 : séparateur virgule, fins de ligne CRLF, guillemets doubles autour des valeurs qui
  contiennent une virgule, un guillemet ou un retour à la ligne (`Nuage, "Cloud" SA` devient
  `"Nuage, ""Cloud"" SA"`).
- Fichiers en UTF-8 sans BOM.
- Sortie déterministe : ordre des entrées et dates fixes, donc un même paquet produit toujours les mêmes octets et
  la même empreinte, ce qui permet de comparer deux générations ou de prouver ce qui a été déposé.

```java
byte[] zip = new XbrlCsvWriter().ecrire(paquet);
String empreinte = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));

try (OutputStream sortie = Files.newOutputStream(Path.of(paquet.nom() + ".zip"))) {
    new XbrlCsvWriter().ecrire(paquet, sortie);
}
```

### Relecture d'un paquet : `XbrlCsvReader`

`lire(byte[])` ou `lire(InputStream)` reconstruit un `PaquetDeRapport`, par exemple à partir du dépôt de l'année
précédente d'un client. Un paquet écrit puis relu redonne le même nom, le même point d'entrée, les mêmes paramètres,
indicateurs et tableaux.

- Le paquet est repéré par son `reports/report.json` ; le point d'entrée vient de `documentInfo.extends`.
- Chaque autre `.csv` de `reports/` devient une `Table` dont le code est déduit du nom de fichier ; la première ligne
  est l'en-tête des colonnes.
- Tolérant à l'entrée : BOM UTF-8 et fins de ligne LF seules acceptés, lignes vides finales ignorées.

Les archives malveillantes ou cassées sont refusées par une `IllegalArgumentException` :

| Cas | Message |
|---|---|
| Chemin absolu ou qui remonte (`../`) | `Paquet invalide : chemin interdit ...` |
| Plus de 500 entrées | `Paquet invalide : trop d'entrées` |
| Plus de 200 Mo une fois décompressé | `Paquet invalide : taille décompressée excessive` |
| Pas un ZIP, ou ZIP vide | `Paquet invalide : archive vide ou non ZIP` |
| `reports/report.json` absent | `Paquet invalide : reports/report.json absent` |
| `report.json` illisible ou sans `documentInfo.extends` | `Paquet invalide : ...` |
| Guillemet non fermé dans un CSV | `CSV invalide : guillemet non fermé` |

Une erreur d'entrée/sortie du flux lu est remontée en `UncheckedIOException`.

### Contrôles structurels : `ValidateurStructurel`

`valider(paquet)` renvoie la liste des anomalies, vide si le paquet est correct. Ces contrôles ne dépendent pas de
la taxonomie : ils repèrent ce qui ferait rejeter le paquet avant même la lecture de son contenu.

Chaque `MessageDeValidation` porte une gravité (`BLOQUANT` empêche le dépôt, `ALERTE` est à examiner), un code de
règle stable, et quand c'est pertinent le tableau, le numéro de ligne de données (1 = première ligne après l'en-tête)
et la colonne concernés, plus un texte lisible.

```java
List<MessageDeValidation> messages = new ValidateurStructurel().valider(paquet);
boolean deposable = messages.stream().noneMatch(m -> m.gravite() == MessageDeValidation.Gravite.BLOQUANT);
messages.forEach(m -> System.out.printf("%s %s %s ligne %s : %s%n",
        m.gravite(), m.code(), m.table(), m.ligne(), m.texte()));
```

| Code | Gravité | Contrôle |
|---|---|---|
| `XC-NOM-PAQUET` | bloquant | Nom du paquet limité aux lettres, chiffres, `.`, `-` et `_` |
| `XC-POINT-ENTREE` | bloquant | Point d'entrée de la taxonomie non vide |
| `XC-PARAM-ENTITE` | bloquant | Paramètre `entityID` présent |
| `XC-PARAM-PERIODE` | bloquant | Paramètre `refPeriod` présent, au format `AAAA-MM-JJ` |
| `XC-PARAM-DEVISE` | bloquant | `baseCurrency`, s'il est fourni, est un code ISO 4217 (`EUR` ou `iso4217:EUR`) |
| `XC-INDICATEUR-DOUBLON` | bloquant | Pas deux indicateurs de dépôt pour le même modèle |
| `XC-TABLE-DOUBLON` | bloquant | Pas deux tableaux de même code |
| `XC-TABLE-NON-DECLAREE` | bloquant | Tout tableau présent est déclaré dans `FilingIndicators.csv` |
| `XC-TABLE-ABSENTE` | bloquant | Tout modèle déclaré est présent dans le paquet |
| `XC-TABLE-CODE` | bloquant | Code de tableau de la forme `B_01.01` |
| `XC-TABLE-COLONNES` | bloquant | Le tableau a au moins une colonne |
| `XC-COLONNE-VIDE` | bloquant | Pas de nom de colonne vide |
| `XC-COLONNE-DOUBLON` | bloquant | Pas deux colonnes de même code |
| `XC-LIGNE-LARGEUR` | bloquant | Chaque ligne a autant de valeurs que de colonnes |
| `XC-LIGNE-VIDE` | alerte | Ligne entièrement vide |

La liste des codes est exposée par `ValidateurStructurel.REGLES`, par exemple pour annoncer combien de règles un
contrôle a vérifiées ; un test garantit qu'elle reprend tous les codes émis.

### Identifiants LEI : `Lei`

Un LEI (ISO 17442) compte 18 caractères alphanumériques majuscules suivis de 2 chiffres de contrôle (ISO 7064
MOD 97-10).

```java
Lei.estValide("506700GE1G29325QX363");      // true
Lei.estValide("506700GE1G29325QX364");      // false : mauvaise clé
Lei.of(" 506700ge1g29325qx363 ").valeur();  // "506700GE1G29325QX363" : espaces retirés, majuscules
Lei.of("506700GE1G29325QX364");             // IllegalArgumentException
Lei.cleDeControle("506700GE1G29325QX3");    // "63"
```

`Lei.of` normalise avant de valider ; le constructeur `new Lei(...)` exige une valeur déjà normalisée.

## Construire

```bash
mvn verify      # compile et lance les tests
mvn install     # installe eu.registredora:xbrl-csv:0.1.0-SNAPSHOT dans le dépôt Maven local
```

La CI (GitHub Actions, Java 21 Temurin) lance `mvn -B verify` et vérifie la syntaxe du script de synchronisation à
chaque push sur `main` et à chaque pull request.

## D'où vient ce code

La bibliothèque est développée dans le dépôt de la plateforme, `anisgodev/Registre_information`, module
`registre-dora/xbrl-csv`, qui reste la source. Ce dépôt en est une copie autonome : même historique git (extrait
avec `git subtree split`), même code, et un `pom.xml` sans parent. Le commit d'origine de la dernière
synchronisation est noté dans `SOURCE-PLATEFORME`.

| Cas | Commande (depuis ce dépôt) |
|---|---|
| Après un changement de `xbrl-csv` dans la plateforme | `scripts/synchroniser.sh <chemin de Registre_information>`, puis `mvn verify` et commit de `src/`, `pom.xml`, `SOURCE-PLATEFORME` |
| Savoir si ce dépôt est à jour | `scripts/synchroniser.sh --verifier <chemin de Registre_information>` |
| Changement fait ici d'abord | `scripts/synchroniser.sh --vers-plateforme <chemin de Registre_information>` recopie `src/` dans la plateforme (un changement de dépendances du `pom.xml` est signalé, à reporter à la main) ; commit là-bas, puis synchronisation normale ici |

Le seul écart permis entre les deux est le `pom.xml` : le script remplace le parent `registre-dora` par la version,
les propriétés Java et les versions de plugins lues dans le pom parent de la plateforme.

Le bac à sable (`anisgodev/registre-bac-a-sable`) garde sa propre copie, synchronisée depuis la plateforme par son
`scripts/synchroniser-moteur.sh`.

## Licence

Tous droits réservés : aucune licence n'est accordée à ce jour.

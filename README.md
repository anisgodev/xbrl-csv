# xBRL-CSV

Bibliothèque Java autonome (sans Spring) pour écrire, lire et contrôler des paquets de rapport xBRL-CSV, en
particulier le registre d'information DORA déposé auprès des autorités (taxonomie DORA de l'EBA).

- modèle d'un paquet : `PaquetDeRapport`, `Table`, `Parametres`, `IndicateurDeDepot` ;
- écriture du `.zip` (`XbrlCsvWriter`, octets identiques à chaque génération) et relecture (`XbrlCsvReader`,
  chemins qui remontent refusés) ;
- contrôles structurels avant dépôt (`ValidateurStructurel`) ;
- validation des LEI selon ISO 17442, clé mod 97 (`Lei`).

Seule dépendance : Jackson. Java 21 ou plus.

```java
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

Les contrôles de la taxonomie elle-même (règles EBA) ne sont pas ici : la plateforme les confie à Arelle.

## Construire

```bash
mvn verify      # compile et lance les tests
mvn install     # installe eu.registredora:xbrl-csv:0.1.0-SNAPSHOT dans le dépôt Maven local
```

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

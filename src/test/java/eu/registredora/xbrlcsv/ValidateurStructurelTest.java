package eu.registredora.xbrlcsv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateurStructurelTest {

    private final ValidateurStructurel validateur = new ValidateurStructurel();

    @Test
    void la_liste_des_regles_reprend_tous_les_codes_emis() throws IOException {
        String source = Files.readString(Path.of("src/main/java/eu/registredora/xbrlcsv/ValidateurStructurel.java"));
        List<String> emis = java.util.regex.Pattern.compile("(?:bloquant|alerte)\\(\"(XC-[A-Z-]+)\"").matcher(source)
                .results().map(m -> m.group(1)).distinct().toList();
        assertThat(emis).isNotEmpty();
        assertThat(ValidateurStructurel.REGLES).containsExactlyInAnyOrderElementsOf(emis);
    }

    @Test
    void un_paquet_correct_ne_produit_aucun_message() {
        assertThat(validateur.valider(PaquetDeRapportFixtures.paquetSimple())).isEmpty();
    }

    @Test
    void detecte_les_tableaux_non_declares_et_absents() {
        Table b0101 = Table.builder("B_01.01", "c0010").ligne("x").build();
        PaquetDeRapport paquet = new PaquetDeRapport("p", "http://x", parametresValides(),
                List.of(new IndicateurDeDepot("B_02.01", true)), List.of(b0101));

        assertThat(codes(validateur.valider(paquet)))
                .containsExactlyInAnyOrder("XC-TABLE-NON-DECLAREE", "XC-TABLE-ABSENTE");
    }

    @Test
    void detecte_les_lignes_de_mauvaise_largeur_et_les_colonnes_en_double() {
        Table table = Table.builder("B_05.01", "c0010", "c0010").ligne("a").build();
        PaquetDeRapport paquet = PaquetDeRapport.avecIndicateursDeduits("p", "http://x", parametresValides(), List.of(table));

        List<MessageDeValidation> messages = validateur.valider(paquet);
        assertThat(codes(messages)).containsExactlyInAnyOrder("XC-COLONNE-DOUBLON", "XC-LIGNE-LARGEUR");
        assertThat(messages).filteredOn(m -> m.code().equals("XC-LIGNE-LARGEUR"))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.table()).isEqualTo("B_05.01");
                    assertThat(m.ligne()).isEqualTo(1);
                });
    }

    @Test
    void detecte_les_parametres_manquants_ou_mal_formes() {
        Parametres parametres = new Parametres(Map.of("refPeriod", "31/12/2026", "baseCurrency", "euro"));
        PaquetDeRapport paquet = PaquetDeRapport.avecIndicateursDeduits("nom avec espace", "http://x", parametres, List.of());

        assertThat(codes(validateur.valider(paquet))).containsExactlyInAnyOrder(
                "XC-NOM-PAQUET", "XC-PARAM-ENTITE", "XC-PARAM-PERIODE", "XC-PARAM-DEVISE");
    }

    @Test
    void signale_une_ligne_vide_en_alerte() {
        Table table = Table.builder("B_06.01", "c0010", "c0020").ligne("", " ").build();
        PaquetDeRapport paquet = PaquetDeRapport.avecIndicateursDeduits("p", "http://x", parametresValides(), List.of(table));

        assertThat(validateur.valider(paquet)).singleElement()
                .satisfies(m -> {
                    assertThat(m.code()).isEqualTo("XC-LIGNE-VIDE");
                    assertThat(m.gravite()).isEqualTo(MessageDeValidation.Gravite.ALERTE);
                });
    }

    private static Parametres parametresValides() {
        return Parametres.registre("rs:X.CON", LocalDate.of(2026, 12, 31), "EUR");
    }

    private static List<String> codes(List<MessageDeValidation> messages) {
        return messages.stream().map(MessageDeValidation::code).toList();
    }
}

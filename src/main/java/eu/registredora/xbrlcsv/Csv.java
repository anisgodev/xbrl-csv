package eu.registredora.xbrlcsv;

import java.util.ArrayList;
import java.util.List;

/** Écriture et lecture CSV selon la RFC 4180 (séparateur virgule, guillemets doubles, fins de ligne CRLF). */
final class Csv {

    static final String FIN_DE_LIGNE = "\r\n";

    private Csv() {
    }

    static String ecrire(List<String> entetes, List<List<String>> lignes) {
        StringBuilder sb = new StringBuilder();
        ecrireLigne(sb, entetes);
        for (List<String> ligne : lignes) {
            ecrireLigne(sb, ligne);
        }
        return sb.toString();
    }

    private static void ecrireLigne(StringBuilder sb, List<String> valeurs) {
        for (int i = 0; i < valeurs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(echapper(valeurs.get(i)));
        }
        sb.append(FIN_DE_LIGNE);
    }

    static String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        boolean aProteger = valeur.indexOf(',') >= 0 || valeur.indexOf('"') >= 0
                || valeur.indexOf('\n') >= 0 || valeur.indexOf('\r') >= 0;
        return aProteger ? '"' + valeur.replace("\"", "\"\"") + '"' : valeur;
    }

    /** Lit un contenu CSV complet ; la première ligne est l'en-tête. Les lignes vides finales sont ignorées. */
    static List<List<String>> lire(String contenu) {
        List<List<String>> lignes = new ArrayList<>();
        List<String> ligne = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean entreGuillemets = false;
        int i = 0;
        if (contenu.startsWith("﻿")) {
            i = 1;
        }
        while (i < contenu.length()) {
            char c = contenu.charAt(i);
            if (entreGuillemets) {
                if (c == '"') {
                    if (i + 1 < contenu.length() && contenu.charAt(i + 1) == '"') {
                        champ.append('"');
                        i++;
                    } else {
                        entreGuillemets = false;
                    }
                } else {
                    champ.append(c);
                }
            } else if (c == '"') {
                entreGuillemets = true;
            } else if (c == ',') {
                ligne.add(champ.toString());
                champ.setLength(0);
            } else if (c == '\r' || c == '\n') {
                ligne.add(champ.toString());
                champ.setLength(0);
                lignes.add(ligne);
                ligne = new ArrayList<>();
                if (c == '\r' && i + 1 < contenu.length() && contenu.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                champ.append(c);
            }
            i++;
        }
        if (entreGuillemets) {
            throw new IllegalArgumentException("CSV invalide : guillemet non fermé");
        }
        if (champ.length() > 0 || !ligne.isEmpty()) {
            ligne.add(champ.toString());
            lignes.add(ligne);
        }
        lignes.removeIf(l -> l.size() == 1 && l.get(0).isEmpty());
        return lignes;
    }
}

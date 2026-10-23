#!/usr/bin/env bash
# Synchronise ce dépôt avec le module registre-dora/xbrl-csv du dépôt de la plateforme
# (anisgodev/Registre_information), qui reste la source : la bibliothèque continue d'y être développée.
#
#   scripts/synchroniser.sh <chemin du dépôt Registre_information>                    recopie la plateforme ici
#   scripts/synchroniser.sh --verifier <chemin du dépôt Registre_information>         signale les écarts
#   scripts/synchroniser.sh --vers-plateforme <chemin du dépôt Registre_information>  reporte src/ d'ici là-bas
#
# Seule adaptation : le pom. Celui de la plateforme hérite de registre-dora ; ici, le parent est remplacé par la
# version, les propriétés Java et les versions de plugins lues dans le pom parent de la plateforme, pour que
# `mvn verify` marche seul.
set -euo pipefail

mode=copie
case "${1:-}" in
  --verifier) mode=verifier; shift ;;
  --vers-plateforme) mode=vers-plateforme; shift ;;
esac
source_depot="${1:?Indiquez le chemin du dépôt Registre_information}"
source_depot="$(cd "$source_depot" && pwd)"
module="$source_depot/registre-dora/xbrl-csv"
parent="$source_depot/registre-dora/pom.xml"
ici="$(cd "$(dirname "$0")/.." && pwd)"
[[ -f "$module/pom.xml" && -f "$parent" ]] || { echo "Pas de module registre-dora/xbrl-csv dans $source_depot" >&2; exit 2; }

copie="$(mktemp -d)"
trap 'rm -rf "$copie"' EXIT
# Fichiers suivis par git seulement : ni target/ ni fichiers locaux.
git -C "$module" ls-files -z -- . | while IFS= read -r -d '' f; do
  mkdir -p "$copie/$(dirname "$f")"
  cp "$module/$f" "$copie/$f"
done

# pom autonome : <parent> remplacé par groupId/version, propriétés Java et pluginManagement repris du parent.
PARENT="$parent" perl -0pi -e '
  local $/; open my $p, "<", $ENV{PARENT} or die; my $parent = <$p>;
  my ($groupe) = $parent =~ m{<groupId>([^<]+)</groupId>};
  my ($version) = $parent =~ m{<version>([^<]+)</version>};
  my ($java) = $parent =~ m{(\s*<java\.version>[^<]*</java\.version>\s*<maven\.compiler\.release>[^<]*</maven\.compiler\.release>\s*<project\.build\.sourceEncoding>[^<]*</project\.build\.sourceEncoding>)};
  my ($plugins) = $parent =~ m{(<pluginManagement>.*?</pluginManagement>)}s;
  defined $java && defined $plugins or die "pom parent inattendu\n";
  s{\s*<parent>.*?</parent>\n}{\n\n    <groupId>$groupe</groupId>}s or die "pas de <parent>\n";
  s{(<artifactId>xbrl-csv</artifactId>)}{$1\n    <version>$version</version>};
  s{<properties>}{<properties>$java};
  s{</project>}{\n    <build>\n        $plugins\n    </build>\n</project>};
' "$copie/pom.xml"

commit="$(git -C "$source_depot" rev-parse HEAD)"
modifie=""
if [[ -n "$(git -C "$source_depot" status --porcelain -- registre-dora/xbrl-csv registre-dora/pom.xml)" ]]; then
  modifie=" (avec des modifications non commitées)"
fi

case "$mode" in
  verifier)
    ecarts=0
    diff -r -q "$copie/src" "$ici/src" || ecarts=1
    diff -q "$copie/pom.xml" "$ici/pom.xml" || ecarts=1
    if (( ecarts )); then
      echo "Écarts avec $source_depot ($commit) : lancez scripts/synchroniser.sh $source_depot" >&2
      exit 1
    fi
    echo "xbrl-csv est identique à $commit."
    ;;
  copie)
    rm -rf "${ici:?}/src"
    cp -r "$copie/src" "$ici/src"
    cp "$copie/pom.xml" "$ici/pom.xml"
    printf 'anisgodev/Registre_information %s%s\n' "$commit" "$modifie" > "$ici/SOURCE-PLATEFORME"
    echo "Synchronisé depuis $commit$modifie. Lancez mvn verify, puis commitez src/, pom.xml et SOURCE-PLATEFORME."
    ;;
  vers-plateforme)
    if [[ -n "$modifie" ]]; then
      echo "Le module xbrl-csv de $source_depot a des modifications non commitées : commitez-les ou rangez-les d'abord." >&2
      exit 1
    fi
    rm -rf "${module:?}/src"
    cp -r "$ici/src" "$module/src"
    if ! diff -q "$copie/pom.xml" "$ici/pom.xml" > /dev/null; then
      echo "Attention : pom.xml diffère (dépendances ?). Reportez le changement à la main dans $module/pom.xml." >&2
    fi
    echo "src/ reporté dans $module. Lancez mvn verify dans registre-dora, commitez, puis lancez ici scripts/synchroniser.sh $source_depot."
    ;;
esac

#!/usr/bin/env bash
# Syncs this repository with the registre-dora/xbrl-csv module of the platform repository
# (anisgodev/Registre_information), which remains the source: the library is still developed there.
#
#   scripts/sync.sh <path to the Registre_information repository>                  copies the platform here
#   scripts/sync.sh --check <path to the Registre_information repository>          reports differences
#   scripts/sync.sh --to-platform <path to the Registre_information repository>    copies src/ from here to there
#
# Only adaptation: the pom. The platform's pom inherits from registre-dora; here the parent is replaced by the
# version, Java properties and plugin versions read from the platform's parent pom, so that `mvn verify` works
# on its own. This repository's groupId (Maven Central namespace), publishing metadata (<url> to <scm>) and
# <profiles> (Central release) are kept.
set -euo pipefail

mode=copy
case "${1:-}" in
  --check) mode=check; shift ;;
  --to-platform) mode=to-platform; shift ;;
esac
source_repo="${1:?Give the path to the Registre_information repository}"
source_repo="$(cd "$source_repo" && pwd)"
module="$source_repo/registre-dora/xbrl-csv"
parent="$source_repo/registre-dora/pom.xml"
here="$(cd "$(dirname "$0")/.." && pwd)"
[[ -f "$module/pom.xml" && -f "$parent" ]] || { echo "No registre-dora/xbrl-csv module in $source_repo" >&2; exit 2; }

copy="$(mktemp -d)"
trap 'rm -rf "$copy"' EXIT
# Files tracked by git only: no target/ and no local files.
git -C "$module" ls-files -z -- . | while IFS= read -r -d '' f; do
  mkdir -p "$copy/$(dirname "$f")"
  cp "$module/$f" "$copy/$f"
done

# Standalone pom: <parent> replaced by groupId/version, Java properties and pluginManagement taken from the parent.
PARENT="$parent" perl -0pi -e '
  local $/; open my $p, "<", $ENV{PARENT} or die; my $parent = <$p>;
  my ($group) = $parent =~ m{<groupId>([^<]+)</groupId>};
  my ($version) = $parent =~ m{<version>([^<]+)</version>};
  my ($java) = $parent =~ m{(\s*<java\.version>[^<]*</java\.version>\s*<maven\.compiler\.release>[^<]*</maven\.compiler\.release>\s*<project\.build\.sourceEncoding>[^<]*</project\.build\.sourceEncoding>)};
  my ($plugins) = $parent =~ m{(<pluginManagement>.*?</pluginManagement>)}s;
  defined $java && defined $plugins or die "unexpected parent pom\n";
  s{\s*<parent>.*?</parent>\n}{\n\n    <groupId>$group</groupId>}s or die "no <parent>\n";
  s{(<artifactId>xbrl-csv</artifactId>)}{$1\n    <version>$version</version>};
  s{<properties>}{<properties>$java};
  s{</project>}{\n    <build>\n        $plugins\n    </build>\n</project>};
' "$copy/pom.xml"
HERE_POM="$here/pom.xml" perl -0pi -e '
  local $/; open my $p, "<", $ENV{HERE_POM} or die; my $here = <$p>;
  my ($group) = $here =~ m{</modelVersion>\s*<groupId>([^<]+)</groupId>};
  my ($meta) = $here =~ m{(    <url>.*</(?:scm|developers|licenses)>\n)}s;
  my ($profiles) = $here =~ m{(\n+    <profiles>.*?</profiles>\n)}s;
  s{(</modelVersion>\s*<groupId>)[^<]+}{$1$group} if defined $group;
  s{(</description>\n)}{$1$meta}s if defined $meta && !m{<licenses>};
  s{\n</project>}{$profiles</project>}s if defined $profiles && !m{<profiles>};
' "$copy/pom.xml"

commit="$(git -C "$source_repo" rev-parse HEAD)"
dirty=""
if [[ -n "$(git -C "$source_repo" status --porcelain -- registre-dora/xbrl-csv registre-dora/pom.xml)" ]]; then
  dirty=" (with uncommitted changes)"
fi

case "$mode" in
  check)
    differs=0
    diff -r -q "$copy/src" "$here/src" || differs=1
    diff -q "$copy/pom.xml" "$here/pom.xml" || differs=1
    if (( differs )); then
      echo "Differences with $source_repo ($commit): run scripts/sync.sh $source_repo" >&2
      exit 1
    fi
    echo "xbrl-csv is identical to $commit."
    ;;
  copy)
    rm -rf "${here:?}/src"
    cp -r "$copy/src" "$here/src"
    cp "$copy/pom.xml" "$here/pom.xml"
    printf 'anisgodev/Registre_information %s%s\n' "$commit" "$dirty" > "$here/PLATFORM-SOURCE"
    echo "Synced from $commit$dirty. Run mvn verify, then commit src/, pom.xml and PLATFORM-SOURCE."
    ;;
  to-platform)
    if [[ -n "$dirty" ]]; then
      echo "The xbrl-csv module in $source_repo has uncommitted changes: commit or stash them first." >&2
      exit 1
    fi
    rm -rf "${module:?}/src"
    cp -r "$here/src" "$module/src"
    if ! diff -q "$copy/pom.xml" "$here/pom.xml" > /dev/null; then
      echo "Warning: pom.xml differs (dependencies?). Apply the change by hand in $module/pom.xml." >&2
    fi
    echo "src/ copied to $module. Run mvn verify in registre-dora, commit, then run scripts/sync.sh $source_repo here."
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

mkdir -p out
mapfile -t sources < <(find src -name '*.java' | sort)
javac -d out "${sources[@]}"

command="${1:-help}"
case "$command" in
  hello)
    java -cp out orbitscope.HelloOrbitScope
    ;;
  warmup)
    java -cp out orbitscope.Warmup
    ;;
  file)
    java -cp out orbitscope.StarlinkCatalog --file "${2:-data/sample-starlink.tle}"
    ;;
  fetch)
    java -cp out orbitscope.StarlinkCatalog --fetch
    ;;
  test)
    java -cp out orbitscope.OrbitScopeTest
    ;;
  *)
    echo "OrbitScope"
    echo
    echo "Usage: ./run.sh <command>"
    echo "  hello    Milestone 0 — print a line"
    echo "  warmup   Milestone 1 — arrays, Satellite class, list methods"
    echo "  file     Milestone 2 — parse a saved TLE file"
    echo "  fetch    Milestone 2 — download live Starlink TLEs from CelesTrak"
    echo "  test     run the built-in checks"
    ;;
esac

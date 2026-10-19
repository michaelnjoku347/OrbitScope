#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -x "$ROOT/mvnw" ]]; then
  MVN="$ROOT/mvnw"
else
  MVN="${MVN:-/tmp/maven/bin/mvn}"
fi

run_main() {
  local main="$1"
  shift
  "$MVN" -q -DskipTests compile exec:java \
    -Dexec.mainClass="$main" \
    -Dexec.args="$*"
}

command="${1:-help}"
shift || true

case "$command" in
  hello)
    run_main orbitscope.HelloOrbitScope
    ;;
  warmup)
    run_main orbitscope.Warmup
    ;;
  file)
    run_main orbitscope.StarlinkCatalog --file "${1:-data/sample-starlink.tle}"
    ;;
  fetch)
    run_main orbitscope.StarlinkCatalog --fetch
    ;;
  iss)
    run_main orbitscope.IssCheck
    ;;
  positions)
    run_main orbitscope.Positions "$@"
    ;;
  future)
    run_main orbitscope.Positions --future "${1:-PT90M}"
    ;;
  api)
    "$MVN" spring-boot:run
    ;;
  web)
    (cd web && npm install && npm run dev)
    ;;
  bench)
    run_main orbitscope.Benchmark "${1:-20}"
    ;;
  test)
    "$MVN" -q test
    ;;
  *)
    echo "OrbitScope"
    echo
    echo "Usage: ./run.sh <command>"
    echo "  hello       Milestone 0 — print a line"
    echo "  warmup      Milestone 1 — arrays, Satellite class, list methods"
    echo "  file        Milestone 2 — parse a saved TLE file"
    echo "  fetch       Milestone 2 — download live Starlink TLEs from CelesTrak"
    echo "  iss         Milestone 3 — ISS lat/lon/alt vs wheretheiss.at"
    echo "  positions   Milestone 3 — compute positions (add --all or --future PT90M)"
    echo "  future      Milestone 3 — ISS and a few Starlinks 90 minutes from now"
    echo "  api         Milestone 4 — start the Spring Boot JSON API on :8080"
    echo "  web         Milestone 5 — Cesium globe at http://localhost:5173"
    echo "  bench       Milestone 7 — CPU vs Kepler-kernel timing"
    echo "  test        run unit tests"
    ;;
esac

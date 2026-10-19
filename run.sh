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
    echo "  if you just want the globe:"
    echo "    ./run.sh api"
    echo "    ./run.sh web"
    echo
    echo "  hello       first java program (println)"
    echo "  warmup      arrays / Satellite class"
    echo "  file        read a saved TLE"
    echo "  fetch       download names from CelesTrak"
    echo "  iss         my ISS numbers vs the internet"
    echo "  positions   print lat/lon/alt"
    echo "  future      same thing 90 min from now"
    echo "  api         java server on :8080"
    echo "  web         globe page on :5173"
    echo "  bench       timing"
    echo "  test"
    ;;
esac

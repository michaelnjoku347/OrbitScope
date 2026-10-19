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
    echo "usage: ./run.sh <cmd>"
    echo "  hello | warmup | file | fetch | iss | positions | future | api | web | bench | test"
    ;;
esac

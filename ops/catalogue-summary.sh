#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

reports=(edi-core/target/surefire-reports/TEST-io.github.ythibos.edi.invariant.*.xml)
pending="$(cat "${reports[@]}" | grep -oE 'pending until slice S[0-9]+[^:<]*' | sed -E 's/^pending until slice //; s/[[:space:]]+$//' || true)"

echo "## Invariant catalogue"
echo
if [[ -z "${pending}" ]]; then
  echo "Every acceptance test in the catalogue passes."
  exit 0
fi
echo "$(echo "${pending}" | wc -l) acceptance tests are pending:"
echo
echo "| Slice | Pending |"
echo "|---|---:|"
echo "${pending}" | sort -V | uniq -c | awk '{ count = $1; $1 = ""; sub(/^ /, ""); print "| " $0 " | " count " |" }'

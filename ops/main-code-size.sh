#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

non_blank_lines() {
  grep -cv '^[[:space:]]*$' "$@" | awk -F: '{ total += $NF } END { print total + 0 }'
}

java_packages() {
  find edi-*/src/main/java -name '*.java' -printf '%h\n' | sort -u
}

console_sources() {
  find edi-console/src -type f \( -name '*.ts' -o -name '*.html' -o -name '*.css' \) ! -name '*.spec.ts'
}

echo "## Main code size"
echo
echo "| Where | Non-blank lines |"
echo "|---|---:|"
total=0
while read -r directory; do
  module="${directory%%/*}"
  package="$(echo "${directory#*/src/main/java/}" | tr / .)"
  lines="$(non_blank_lines "${directory}"/*.java)"
  total=$((total + lines))
  echo "| ${module} \`${package}\` | ${lines} |"
done < <(java_packages)
mapfile -t console < <(console_sources)
console_lines="$(non_blank_lines "${console[@]}")"
total=$((total + console_lines))
echo "| edi-console | ${console_lines} |"
echo "| **Total** | **${total}** |"

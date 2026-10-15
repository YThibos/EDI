#!/usr/bin/env bash
set -euo pipefail

range="$1"
deny_list="${2:-}"
refused=0

cd "$(dirname "$0")/.."

scan_secrets() {
  echo "Secret scan of ${range} with the standard gitleaks rules"
  local workspace
  workspace="$(mktemp -d)"
  printf '[extend]\nuseDefault = true\n' > "${workspace}/rules.toml"
  gitleaks git --log-opts="${range}" --config="${workspace}/rules.toml" --gitleaks-ignore-path="${workspace}" \
    --ignore-gitleaks-allow --redact --no-banner --exit-code 0 --log-level error \
    --report-format json --report-path "${workspace}/report.json" .
  if [[ "$(jq length "${workspace}/report.json")" -gt 0 ]]; then
    jq -r '.[] | "\(.File):\(.StartLine) in commit \(.Commit[0:7]): gitleaks rule \(.RuleID)"' "${workspace}/report.json"
    refused=1
  fi
  rm -rf "${workspace}"
}

refuse_personal_paths() {
  echo "Personal layer check of ${range}"
  local paths
  paths="$(git log --format= --name-only "${range}" | grep -E '^personal/' | sort -u || true)"
  if [[ -n "${paths}" ]]; then
    while IFS= read -r path; do
      echo "${path}: inside the personal layer"
    done <<< "${paths}"
    refused=1
  fi
}

refuse_denied_terms() {
  if [[ -z "${deny_list}" || ! -s "${deny_list}" ]]; then
    echo "No deny-list configured; the personal deny-list check is skipped"
    return 0
  fi
  echo "Personal deny-list check of ${range}"
  local hits
  hits="$( { added_lines; commit_metadata; } | awk -F '\t' -v list="${deny_list}" '
    BEGIN {
      while ((getline entry < list) > 0) {
        number++
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", entry)
        if (entry != "" && entry !~ /^#/) { terms[number] = tolower(entry) }
      }
    }
    { for (n in terms) if (index(tolower($2), terms[n])) print $1 ": deny-list entry " n }')"
  if [[ -n "${hits}" ]]; then
    echo "${hits}" | sort -u
    refused=1
  fi
}

added_lines() {
  git diff --unified=0 --no-color --no-ext-diff --src-prefix=a/ --dst-prefix=b/ "${range}" | awk '
    /^\+\+\+ / { file = substr($0, 7); sub(/\t$/, "", file); next }
    /^@@ / { split($3, start, ","); line = substr(start[1], 2) + 0; next }
    /^\+/ { print file ":" line "\t" substr($0, 2); line++ }'
}

commit_metadata() {
  git log --format='%h%x09%an <%ae> %cn <%ce> %s %b' "${range}" | tr '\n' ' ' | sed 's/ \([0-9a-f]\{7,\}\)\t/\n\1\t/g' |
    awk -F '\t' 'NF > 1 { print "commit " $1 "\t" $2 }'
}

scan_secrets
refuse_personal_paths
refuse_denied_terms

if [[ "${refused}" -ne 0 ]]; then
  echo "Refused: the outgoing commits carry a secret or personal material." >&2
  exit 1
fi
echo "Clear: no secret and no personal material in ${range}"

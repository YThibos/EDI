#!/usr/bin/env bash
set -euo pipefail

host="${SONAR_HOST_URL:-http://localhost:9000}"
gate="EDI overall code"
factory_password="admin"
admin_password="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')Aa1!"

sonar() {
  curl --silent --show-error --fail --user "admin:${admin_password}" "$@"
}

await_server() {
  for _ in $(seq 1 120); do
    if curl --silent "${host}/api/system/status" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  echo "SonarQube at ${host} did not come up within ten minutes." >&2
  return 1
}

replace_factory_password() {
  curl --silent --show-error --fail --user "admin:${factory_password}" --request POST "${host}/api/users/change_password" \
    --data-urlencode login=admin --data-urlencode "previousPassword=${factory_password}" --data-urlencode "password=${admin_password}"
}

create_token() {
  sonar --request POST "${host}/api/user_tokens/generate" --data-urlencode "name=edi-analysis-$(date +%s)" \
    | sed -E 's/.*"token":"([^"]+)".*/\1/'
}

rating_metrics() {
  if sonar "${host}/api/settings/values?keys=sonar.multi-quality-mode.enabled" | grep -q '"value":"true"'; then
    echo software_quality_reliability_rating software_quality_security_rating software_quality_maintainability_rating
  else
    echo reliability_rating security_rating sqale_rating
  fi
}

create_overall_code_gate() {
  sonar --request POST "${host}/api/qualitygates/create" --data-urlencode "name=${gate}" > /dev/null
  for metric in $(rating_metrics); do
    add_condition "${metric}" 1
  done
  add_condition duplicated_lines_density 3
  sonar --request POST "${host}/api/qualitygates/set_as_default" --data-urlencode "name=${gate}"
}

add_condition() {
  sonar --request POST "${host}/api/qualitygates/create_condition" --data-urlencode "gateName=${gate}" \
    --data-urlencode "metric=$1" --data-urlencode op=GT --data-urlencode "error=$2" > /dev/null
}

publish_token() {
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "::add-mask::$1"
    echo "SONAR_TOKEN=$1" >> "${GITHUB_ENV}"
  else
    echo "SONAR_TOKEN=$1"
  fi
}

await_server
replace_factory_password
create_overall_code_gate
publish_token "$(create_token)"

#!/usr/bin/env bash
set -euo pipefail

checkout="$(cd "$(dirname "$0")/.." && pwd)"
home="${EDI_HOME:-${HOME}/.edi}"
bin="${EDI_BIN:-${HOME}/.local/bin}"
skills="${home}/.claude/skills"

build() {
  (cd "${checkout}" && ./mvnw -B -ntp -Pnative verify)
}

install_the_executables() {
  mkdir -p "${bin}"
  install -m 755 "${checkout}/edi-cli/target/edi" "${bin}/edi"
  install -m 755 "${checkout}/edi-core/target/edi-core" "${bin}/edi-core"
}

link_the_skills() {
  mkdir -p "${skills}"
  find "${skills}" -maxdepth 1 -type l -lname "${checkout}/*" -delete
  for skill in "${checkout}"/claude/skills/*/ "${checkout}"/personal/skills/*/; do
    [[ -d "${skill}" ]] || continue
    ln -sfn "${skill%/}" "${skills}/$(basename "${skill}")"
  done
}

build
install_the_executables
link_the_skills
echo "EDI is installed: ${bin}/edi and ${bin}/edi-core, with the skills linked into ${skills}."

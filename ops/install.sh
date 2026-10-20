#!/usr/bin/env bash
set -euo pipefail

checkout="$(cd "$(dirname "$0")/.." && pwd)"
home="${EDI_HOME:-${HOME}/.edi}"
bin="${EDI_BIN:-${HOME}/.local/bin}"
skills="${home}/.claude/skills"
toolchain="${checkout}/mise.toml"

require_the_pinned_toolchain() {
  if ! command -v mise >/dev/null; then
    refuse "EDI builds with the toolchain ${toolchain} pins, and mise, which runs it, is not on the PATH; install mise, run mise install in ${checkout} and try again."
  fi
  local missing
  missing="$(cd "${checkout}" && mise ls --local --missing --no-header)"
  if [[ -n "${missing}" ]]; then
    refuse "EDI builds with the toolchain ${toolchain} pins, and part of it is not installed; run mise install in ${checkout} and try again."$'\n'"${missing}"
  fi
}

refuse() {
  echo "$1" >&2
  exit 1
}

build() {
  (cd "${checkout}" && mise exec -- ./mvnw -B -ntp -Pnative verify)
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

require_the_pinned_toolchain
build
install_the_executables
link_the_skills
echo "EDI is installed: ${bin}/edi and ${bin}/edi-core, with the skills linked into ${skills}."

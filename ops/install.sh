#!/usr/bin/env bash
set -euo pipefail

checkout="$(cd "$(dirname "$0")/.." && pwd)"
home="${EDI_HOME:-${HOME}/.edi}"
bin="${EDI_BIN:-${HOME}/.local/bin}"
skills="${home}/.claude/skills"
installed_build_record="${home}/state/installed-build"
toolchain="${checkout}/mise.toml"

require_the_pinned_toolchain() {
  require_mise
  install_the_missing_pinned_tools
}

require_mise() {
  if ! command -v mise >/dev/null; then
    refuse "EDI builds with the toolchain ${toolchain} pins, and mise, which runs it, is not on the PATH; install mise and try again."
  fi
}

install_the_missing_pinned_tools() {
  local missing
  missing="$(missing_pinned_tools)"
  if [[ -z "${missing}" ]]; then
    return
  fi
  echo "Installing the pinned tools this machine lacks: $(listed "${missing}")"
  local tools
  mapfile -t tools <<< "${missing}"
  if ! (cd "${checkout}" && mise install "${tools[@]}"); then
    refuse_the_failed_install "${missing}"
  fi
}

missing_pinned_tools() {
  (cd "${checkout}" && mise ls --local --missing --no-header) | awk '!named[$1]++ { print $1 }'
}

listed() {
  paste -sd, - <<< "$1" | sed 's/,/, /g'
}

refuse_the_failed_install() {
  local still_missing
  still_missing="$(missing_pinned_tools || true)"
  refuse "EDI could not install the pinned tools ${toolchain} names: $(listed "${still_missing:-$1}"); mise's output above says why, and the installed EDI is unchanged."
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
  install -m 755 "${checkout}/ops/helloedi" "${bin}/helloedi"
}

keep_the_home_for_its_owner_alone() {
  mkdir -p "${home}"
  chmod 700 "${home}"
}

link_the_skills() {
  mkdir -p "${skills}"
  find "${skills}" -maxdepth 1 -type l -lname "${checkout}/*" -delete
  for skill in "${checkout}"/claude/skills/*/ "${checkout}"/personal/skills/*/; do
    [[ -d "${skill}" ]] || continue
    ln -sfn "${skill%/}" "${skills}/$(basename "${skill}")"
  done
}

record_the_installed_build() {
  mkdir -p "$(dirname "${installed_build_record}")"
  git -C "${checkout}" rev-parse HEAD > "${installed_build_record}"
}

require_the_pinned_toolchain
build
install_the_executables
keep_the_home_for_its_owner_alone
link_the_skills
record_the_installed_build
echo "EDI is installed: ${bin}/edi, ${bin}/edi-core and ${bin}/helloedi, built from $(<"${installed_build_record}"), with the skills linked into ${skills}."

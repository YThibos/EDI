#!/usr/bin/env bash
set -eu

print_rules() {
  cat <<'EOF'
Craftsmanship rules this change must meet; review it against them as acceptance criteria:
- Methods are short and do one thing.
EOF
}

print_rules

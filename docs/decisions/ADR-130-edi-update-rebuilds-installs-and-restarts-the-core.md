# ADR-130: edi update pulls, builds and tests natively, installs, and restarts the core while workers keep running

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.3: the `edi-update` skill owns `/update` and "update EDI": pull, build, test, install, restart the core, re-read the anchor.
Section 17.2: the native profile runs before every install; section 6: `ops/install.sh` builds the native executables and installs `edi`, `edi-core`, the hooks and the skills' links.
The clone of the EDI repository on each machine is `~/EDI`, used only to build and install.
A core restart is a non-event (I-23), so workers need not stop for it.

**Decision:** `edi update` works on the checkout named by `EDI_CHECKOUT`, or `~/EDI`: it refuses a directory that is not a clone and a clone with local changes, pulls fast-forward only, and runs `ops/install.sh`.
`ops/install.sh` runs `./mvnw -Pnative verify`, installs `edi` and `edi-core` into `EDI_BIN` (default `~/.local/bin`) and links every skill of `claude/skills/` and the checkout's `personal/skills/` into `$EDI_HOME/.claude/skills`, where the primary, working in the EDI home, finds them.
When the install succeeds, the command asks the running core to stop for a restart, which stops no worker, waits until it is gone and starts the new core as `edi up` does; when the install fails, the installed EDI and the running core are left as they were.
It ends by naming the commit it updated to and the anchor to re-read, because a running session keeps the system prompt it started with.

**Motivation:** One command takes EDI from a merged change to a running core with no step for the captain to forget, and refuses rather than overwrite a clone with work in it.

**Alternatives considered:**
- Building inside the running core - the core would have to replace and restart itself.
- Installing with the JVM build only - a change that breaks the native image would reach the machine.

**Consequences:**
- Good: the primary runs it in the background and reports the outcome.
- Bad: the native build and its tests take minutes.

**Revisit when:** Releases are published as built executables.

**Sources:** EDI rewrite specification, sections 5.5, 6, 10.3 and 17.2, I-23; ADR-129; `edi-cli` `UpdateCommand`, `EdiCheckout`, `LocalCore`, `ops/install.sh`, `application.lifecycle.CoreStop`, `claude/skills/edi-update/SKILL.md`.

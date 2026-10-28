---
name: update
description: >-
  Owns updating you: pulling the EDI repository, building and testing it natively, installing it, restarting the core and re-reading the anchor.
  Load it when the captain types /update or asks to update EDI.
user-invocable: true
---

# update

## Steps

1. Run `edi update` in the background: the native build and its tests take minutes, and one call may outlive a foreground command.
2. It works in `~/EDI`, or in the clone `EDI_CHECKOUT` names, and refuses one with local changes.
3. It pulls with `--ff-only`, then runs `ops/install.sh`, which runs `./mvnw -Pnative verify` with the toolchain the clone's `mise.toml` pins and installs `edi`, `edi-core`, `helloedi` and the skill links, recording the commit it built.
4. A missing pinned tool, a failed build or a failed test leaves the installed build and the running core unchanged; report the failure with its output.
5. It then restarts the core: it waits until the old core has let go of the home, starts the new one and waits for it to answer; running workers keep running, and the store makes the restart a non-event.
6. When it says no core runs the new build, run `edi up --core-only` once the old core has exited, as the command says, and report the outcome.
7. Read `claude/primary/CLAUDE.md` of the clone again, as the command says: the rules may have changed.
8. A new primary session loads the new anchor, skills and memory; offer the captain `edi up` when the anchor changed.

## Limits

- You never edit Claude Code's settings for yourself: the captain adds their own allow rules, such as `Bash(edi *)`.
- Machine tools are the captain's own `/laptop-update`, not yours.
- Report the new version in one line, with what changed for the captain in outcomes.

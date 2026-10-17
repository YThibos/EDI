---
name: edi-update
description: >-
  Owns updating EDI itself: pulling the EDI repository, building and testing it natively, installing it, restarting the core and re-reading the anchor.
  Load it when the captain types /update or asks to update EDI.
user-invocable: true
---

# edi-update

## Steps

1. Run `edi update` in the background: the native build and its tests take minutes, and one call may outlive a foreground command.
2. It works in `~/EDI`, or in the clone `EDI_CHECKOUT` names, and refuses one with local changes.
3. It pulls with `--ff-only`, then runs `ops/install.sh`, which runs `./mvnw -Pnative verify` and installs `edi`, `edi-core` and the skill links.
4. A failed build or test leaves the installed EDI and the running core unchanged; report the failure with its output.
5. It then restarts the core: running workers keep running, and the store makes the restart a non-event.
6. Read `claude/primary/CLAUDE.md` of the clone again, as the command says: the rules may have changed.
7. A new primary session loads the new anchor, skills and memory; offer the captain `edi up` when the anchor changed.

## Limits

- EDI never edits Claude Code's settings for itself: the captain adds his own allow rules, such as `Bash(edi *)`.
- Machine tools are the captain's own `/laptop-update`, not EDI's.
- Report the new version in one line, with what changed for him in outcomes.

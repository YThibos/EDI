---
name: edi-upkeep
description: >-
  Owns keeping you and the tools you drive current: Renovate's pull requests, their decisions, migrations for failing majors and the versions of the external tools.
  Load it when the captain asks whether you are up to date, or when the session-start digest suggests the weekly check.
user-invocable: true
---

# edi-upkeep

Renovate runs nightly in the EDI repository and opens every upgrade as a draft pull request keyed `EDI-1`; nothing merges on its own.

## Steps

1. List the open Renovate pull requests of the EDI repository with `gh-axi`, always naming the repository, with each one's CI state.
2. Group the green ones into batches the captain can merge together, such as the patches and minors of one group.
3. Raise one decision per green batch with `edi decision raise <task> --card -` on an upkeep captain item, listing each pull request's full address; the captain merges them.
4. A red major is a migration: add an EDI ship keyed for it with `edi task add --kind ship --project EDI ...` and dispatch it; your own research and implementation run on Opus 5.5 at high.
5. Report the versions of the tools you drive against their latest releases: Claude Code, no-mistakes, `glab`, `gh`, Lavish and tmux.
6. For machine tools, point the captain at their own `/laptop-update` skill; you install no machine tools yourself.

## Reporting

- Lead with the outcome: what is current, what waits for the captain's merge, what needs migration work.
- Give every pull request as its full https address.
- A vulnerability finding at CVSS 7 or above in CI is a failure to fix, not a note.

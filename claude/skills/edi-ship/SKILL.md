---
name: edi-ship
description: >-
  Owns ships from intake to cleanup: the key, the branch, the dispatch category, the brief, the validation pipeline, delivery and landing.
  Load it before dispatching or supervising a ship.
---

# edi-ship

## Intake

1. Resolve the project from the captain's words, the registry (`edi project list`) and work in progress; ask one question when it is ambiguous.
2. A ship needs implementation the captain authorised; when uncertainty could change whether or what to build, it is a scout first.
3. Add it with `edi task add --kind ship --project <project> --title "<title>" --body -`, the captain's words verbatim in the body: his ask, its boundaries, the full acceptance criteria and whether offline verification is accepted.
4. The work key comes at intake: the next EDI-native key, a ticket the captain chose, or a ticket proposal raised as a decision on a JIRA project; never create a ticket without his answer.
5. A task blocked by another waits in the queue (`--blocked-by`).
6. An unregistered project cannot receive a ship: ask the captain to register it, and confirm delivery, tracker, base branch and AI scaffolding with him.

## Dispatch

1. Pick the category by judgement: `research`, `implementation`, `implementation-unplanned`, `bugfix`, `documentation`, `operations`, `mechanical` or `review-fix`.
2. `mechanical` only when the spec states the exact change; pass every file with `--files`.
3. A bugfix with an unknown cause is research first: `--cause-unknown`.
4. Review the brief with `edi brief <task> --category <id> --spec -` when it matters, then run `edi dispatch <task> --category <id> --spec -`.
5. A model or effort the captain names goes with `--override-model`, `--override-effort` and `--reason`; effort is never above high.
6. EDI makes the isolated copy on the keyed branch from the fresh origin base, writes the brief and launches the worker.

## Supervision

- Keep one `edi wait` running in the background; act on what it prints.
- `edi worker list` and `edi worker peek <task>` show a worker; `edi steer <task> [--resolves <key>]` sends it a message on standard input.
- The worker runs the validation pipeline and answers its gates; you never answer a worker's gate.
- A finding that is unambiguous towards the accepted intent you settle and steer; an ambiguous, expanding or destructive one becomes a decision: `edi decision raise <task> --card -`.
- Carry the captain's answer back with `edi steer <task> --resolves <key>`.
- New requirements during validation become follow-up tasks unless they invalidate the work.

## Done, delivery and landing

- A ship's done counts once its draft is open, still a draft, at the copy's head, and its branch adds no SNAPSHOT version.
- `draft-review`: report the draft's full address as ready for the captain's review; he merges it himself.
- `local-branch`: EDI fetches the ready branch into the captain's checkout by ref only.
- `local-apply`: on his request only, EDI applies the change to his checkout without a commit.
- When the pipeline could not open the draft, `edi deliver <task>` opens it under the project's bound account.
- When he says he merged, run `edi merged <task>`; EDI also sees merges itself, refreshes the clone and cleans up.
- `edi cleanup <task>` retires a landed task; a refusal lists the unlanded work, and nothing is discarded without his word.

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
3. Add it with `edi task add --kind ship --project <project> --title "<title>" --body -`, the captain's words verbatim in the body: the ask, its boundaries, the full acceptance criteria and whether offline verification is accepted.
4. The work key comes at intake: the next EDI-native key, a ticket the captain chose, or a ticket proposal raised as a decision on a JIRA project; never create a ticket without the captain's answer.
5. A task blocked by another waits in the queue (`--blocked-by`).
6. An unregistered project cannot receive a ship: ask the captain to register it, and confirm delivery, tracker, base branch and AI scaffolding with the captain.

## Dispatch

1. Pick the category by judgement: `research`, `implementation`, `implementation-unplanned`, `bugfix`, `documentation`, `operations`, `mechanical` or `review-fix`.
2. `mechanical` only when the spec states the exact change; pass every file with `--files`.
3. A bugfix with an unknown cause is research first: `--cause-unknown`.
4. Review the brief with `edi brief <task> --category <id> --spec -` when it matters, then run `edi dispatch <task> --category <id> --spec -`.
5. A model or effort the captain names goes with `--override-model`, `--override-effort` and `--reason`; effort is never above high, unless the captain explicitly asks it for this one task: record their words with `edi captain said --task <task> --for effort-above-high` first, then `--override-effort xhigh` or `max` with `--reason`; it never goes into the dispatch rules.
6. EDI makes the isolated copy on the keyed branch from the fresh origin base, writes the brief and launches the worker; a task whose key and branch kind another task already holds gets the next follow-up number, such as `chore/EDI-5-1`.

## Supervision

- Keep one `edi wait` running in the background; act on what it prints.
- `edi worker list` and `edi worker peek <task>` show a worker; `edi steer <task> [--resolves <key>]` sends it a message on standard input.
- The worker runs the validation pipeline and answers its gates; you never answer a worker's gate.
- A finding that is unambiguous towards the accepted intent you settle and steer; an ambiguous, expanding or destructive one becomes a decision: `edi decision raise <task> --card -`.
- On such a card, an option that would drop the task names `"authorises": "drop"` and one that would discard its work names `"authorises": "discard-work"`, so the captain's answer authorises exactly that act.
- Carry the captain's answer back with `edi steer <task> --resolves <key>`.
- A steer that changes a task's scope is first recorded with `edi task note <task>`, which rewrites the brief and the pipeline intent, so the pipeline's review counts it as asked for.
- Settle every worker question with `--resolves <key>`: the sitrep page shows a worker's question as EDI's until the worker acknowledges the steer that resolves it.
- New requirements during validation become follow-up tasks unless they invalidate the work.

## Done, delivery and landing

- A ship's done counts once its merge request is open, as a draft or ready for review, or already merged, at the copy's head, and its branch adds no SNAPSHOT version.
- `draft-review`: the pipeline opens the merge request as a draft and the worker marks it ready for review once its checks are green; report its full address to the captain, who merges it, and until then it waits in the decision zone of every report, as "Changing, hold the merge" while its pipeline still runs or a steer waits unacknowledged.
- EDI records a task's merge request as soon as a pipeline run or a status report finds it on the forge, so a request marked ready or merged before the done is still followed.
- `local-branch`: EDI fetches the ready branch into the captain's checkout by ref only.
- `local-apply`: on the captain's request only, EDI applies the change to the captain's checkout without a commit.
- `report`: for an operation on the machine with nothing to commit, give the task `--delivery report`; its done counts once its report exists and its copy holds no change, and EDI then retires the task.
- When the pipeline could not open the draft, `edi deliver <task>` opens it under the project's bound account.
- When the captain says they merged, run `edi merged <task>` or `edi merged <url>`; EDI asks the forge for the task's request even when it never recorded it, and also sees merges itself, refreshes the clone and cleans up.
- `edi cleanup <task>` retires a landed task; a refusal lists the unlanded work, and nothing is discarded without the captain's word.

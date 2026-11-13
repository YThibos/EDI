---
name: edi-ship
description: >-
  Owns ships from intake to cleanup: the key, the branch, the dispatch category, the brief, the validation pipeline, delivery and landing.
  Load it before dispatching or supervising a ship.
---

# edi-ship

## Intake

1. Resolve the project from the captain's words, the registry (`edi project list`) and work in progress; ask one question when it is ambiguous.
   Work on the EDI project goes to the upstream backlog with `edi-backlog-item` instead of a dispatch here, unless the captain asks for it to run on this machine.
2. A ship needs implementation the captain authorised; when uncertainty could change whether or what to build, it is a scout first.
   A request that will take more than one merge request is never dispatched as a ship: queue a scout on it first (load `edi-scout`), and promote the plan the captain settles on its board into one ship per slice; a request that takes one merge request stays a ship.
   Measured: features specified on a board first reached a sliced plan in 1h27 and 7h01, against 18h27 without, for 1% to 5% of a week's tokens (`docs/research/EDI-75-measurements.md`).
3. Add it with `edi task add --kind ship --project <project> --title "<title>" --body -`, the captain's words verbatim in the body: the ask, its boundaries, the full acceptance criteria and whether offline verification is accepted.
4. The work key comes at intake: the next EDI-native key, a ticket the captain chose, the issue's number on a project keyed by its GitHub issues, or a ticket proposal raised as a decision on a JIRA project; never create a ticket without the captain's answer.
   A key the captain gives after intake goes on with `edi task key <task> <KEY>`, citing the captain's words with `--message <n>` when it answers an open ticket decision.
5. A task blocked by another waits in the queue until that task's work lands (`--blocked-by`); a plan of slices goes in with `edi plan add --from -`, each slice waiting as the plan names (`edi plan show <key>`).
6. An unregistered project cannot receive a ship: ask the captain to register it, and confirm delivery, tracker, base branch and AI scaffolding with the captain.

## Dispatch

1. Pick the category by judgement: `research`, `implementation`, `implementation-unplanned`, `bugfix`, `documentation`, `operations`, `mechanical` or `review-fix`.
2. `mechanical` only when the spec states the exact change; pass every file with `--files`.
3. A bugfix with an unknown cause is research first: `--cause-unknown`.
4. Review the brief with `edi brief <task> --category <id> --spec -` when it matters, then run `edi dispatch <task> --category <id> --spec -`.
5. A model or effort the captain names goes with `--override-model`, `--override-effort` and `--reason`; effort is never above high, unless the captain explicitly asks it for this one task: record their message with `edi captain said --task <task> --for effort-above-high --message <n>` first, unless they chose a dialog option that opens with `effort-above-high` and names the task and no other, then `--override-effort xhigh` or `max` with `--reason`; it never goes into the dispatch rules.
6. Your core makes the isolated copy on the keyed branch from the fresh origin base, writes the brief and launches the worker; a task whose key and branch kind another task already holds gets the next follow-up number, such as `chore/EDI-5-1`.

## Supervision

- Keep the one wake stream live: the Monitor tool running `edi wait --follow` alone; act on each wake it prints, and start it again when it ends.
- `edi worker list`, `edi worker show <task>` (its whole last status and open question, from the store) and `edi worker peek <task>` (its pane) show a worker; `edi steer <task> [--resolves <key>]` sends it a message on standard input.
- The worker runs the validation pipeline and answers its gates; you never answer a worker's gate.
- A finding that is unambiguous towards the accepted intent you settle and steer; an ambiguous, expanding or destructive one becomes a decision: `edi decision raise <task> --card -`.
- Such a decision waits in the console for its round of decisions, and the core wakes you when the round is due: never put it to the captain on its own before then.
- Only a decision that blocks the captain's current work gets `"urgent": true` on its card, which puts it before them at once.
- On such a card, an option that would drop the task names `"authorises": "drop"` and one that would discard its work names `"authorises": "discard-work"`, so the captain's answer authorises exactly that act.
- Carry the captain's answer back with `edi steer <task> --resolves <key>`.
- A steer that changes a task's scope is first recorded with `edi task note <task>`, which rewrites the brief and the pipeline intent, so the pipeline's review counts it as asked for.
- Settle every worker question with `--resolves <key>`: the sitrep page shows a worker's question as yours until the worker acknowledges the steer that resolves it.
- New requirements during validation become follow-up tasks unless they invalidate the work.

## Done, delivery and landing

- A ship's done counts once its merge request is open, as a draft or ready for review, or already merged, at the copy's head, and its branch adds no SNAPSHOT version.
- `draft-review`: the pipeline opens the merge request as a draft and the worker marks it ready for review once its checks are green, or once the pipeline passes on a project whose CI does not run, with the local verification as the evidence; report its full address to the captain, who merges it, and until then it waits in the decision zone of every report, as "Changing, hold the merge" while its pipeline still runs, a steer waits unacknowledged, or its worker is back at work after a done.
- You record a task's merge request as soon as a pipeline run or a status report finds it on the forge, so a request marked ready or merged before the done is still followed.
- `local-branch`: you fetch the ready branch into the captain's checkout by ref only.
- `local-apply`: on the captain's request only, you apply the change to the captain's checkout without a commit.
- `report`: for an operation on the machine with nothing to commit, give the task `--delivery report`; its done counts once its report exists and its copy holds no change, and you then retire the task.
- When the pipeline could not open the draft, `edi deliver <task>` opens it under the project's bound account.
- When the captain says they merged, run `edi merged <task>` or `edi merged <url>`; you ask the forge for the task's request even when you never recorded it, and you also see merges yourself, refresh the clone and clean up.
- `edi cleanup <task>` retires a landed task; a refusal lists the unlanded work, and nothing is discarded without the captain's word.

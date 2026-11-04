# The turn report is the captain's attention list, and the sitrep is the overview

**Kind:** Design

## Context

Every state-changing turn ends with a report pasted into the chat (I-21), and every open decision appears in every report until it is answered (I-19).
That report was the full boxed captain report: the decision zone with every card in full, then done, ongoing, next, parked and the backlog, headed by the presence and the heavy runs.
With several tasks running in parallel, the captain scrolled past the same cards and the same state in every turn, while the chat is where the primary and the captain talk about what needs him.
The overview of the fleet already has two homes that the captain opens on purpose: `edi sitrep` and the console's sitrep page ([console](console.md)).

## Decision

`POST /api/report`, which `edi report` sends with the turn's items or, without `--turn`, an empty turn, renders a `TurnReport` with `TurnReportPresenter`; `GET /api/report` stays the sitrep and renders the boxed `CaptainReport` unchanged ([captain-report](captain-report.md)).
The turn report holds only what needs the captain: the calendar line while the calendar cannot be read, the decision zone when something waits on him, and this turn's done and next items when there are any.
The decision zone keeps everything that waits on him, built by `WaitingEntries` for both reports: every open decision, every research board with questions or bound decisions, every request ready for his review with its full address, and every request still changing.
The zone also draws a `Waiting on EDI` line for each worker whose question the primary has not answered (`DecisionZone.WorkerWaiting`): the task's title, the question in its first twelve words and how long it has waited; it counts as nothing open for the captain, and goes once the steer that resolves the question's key is acknowledged.
So the turn report leaves the zone out, and the sitrep prints `Nothing waiting on you`, only when neither a call for the captain nor a worker question waits (`WaitingOnTheCaptain.hasNothingWaiting`).
A decision's card is drawn in full when it was raised or re-raised after the last report shown in an earlier turn of the calling session (`TurnReport.showsTheCardOf`); otherwise it is one line, `DecisionZone.Reminder`, with its number, its title and the recommended option.
`PrimaryTurn.previousReportAt` carries that moment: a new turn takes the latest report of the turn before it, or that turn's own previous report when it rendered none, and `PrimarySessions.previousReportOf` reads it for the controller.
A report with no session, or a session that has seen no report yet, draws every card in full.
Ongoing work, parked work, the backlog, the presence and the heavy-runs `Builds:` line are state, not calls for the captain, so the turn report leaves them out.
A failure or blocker that needs the captain always reaches the turn report, as a decision or as a done item, and is never left to the sitrep.
A turn report with nothing in it is the one line `Nothing needs you; /sitrep shows the rest.`
The Stop hook's reason names `edi report --turn -`, and a sitrep rendered in the turn still satisfies it ([wakes-and-turn-end](wakes-and-turn-end.md)).
`edi sitrep --turn -` posts a state-changing turn's items to `POST /api/report/sitrep`, which draws them in the sitrep's own done and next sections, so a turn whose report is the sitrep keeps them.

## Why

The chat is the conversation between the captain and the primary, so its report carries only what he must decide, review or fix, and what this turn did and does next.
A card he has already seen needs only enough to recognise and answer it, and its one line keeps I-19 without repeating the card.
Measuring "already seen" from the last report of an earlier turn means a card raised between turns, or in a quiet turn without a report, still shows in full in the first report after it, and a report rendered twice in one turn shows the same cards twice.

## Alternatives rejected

- Trimming the sitrep itself - the captain asked for the overview to stay where it is, and the golden files pin its approved layout.
- Drawing a card in full only when its raising falls inside the current turn - a decision raised while the primary waited between turns would never show in full.
- Leaving a long-open decision out of later reports - I-19 requires every open decision in every report.
- Reporting state such as ongoing work in the turn's own items - it would grow the attention list back into the overview.

## Consequences

- Good: a turn report is as long as what needs the captain, and a quiet turn's report is one line.
- Good: the sitrep, the console page and their golden files did not change.
- Bad: the turn report no longer shows what runs or waits in the backlog; the captain asks for `/sitrep` or opens the console page for that.
- Bad: a card is drawn in full only once per session, so a captain who missed the report that showed it reads the card with `/sitrep` or on the console.

## When to revisit

When the captain asks for something more in the turn report, or the report is read somewhere other than the chat.

## How we got here

- 28/10/2026: with many parallel tasks the report had become long scrolling, so the captain made the turn report the session's attention list and left the overview to the sitrep (EDI-133).
- 03/11/2026: a worker waited an hour on a question the primary never saw while every turn report said nothing was waiting, so each open worker question got a `Waiting on EDI` line in the zone (EDI-154).

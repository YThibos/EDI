# The turn report is the captain's attention list, and the sitrep is the overview

**Kind:** Design

## Context

Every turn that changes something for the captain ends with the turn report, shown once in the chat (I-21), and every open decision appears in every report that is shown until it is answered (I-19).
That report was the full boxed captain report: the decision zone with every card in full, then done, ongoing, next, parked and the backlog, headed by the presence and the heavy runs.
With several tasks running in parallel, the captain scrolled past the same cards and the same state in every turn, while the chat is where the primary and the captain talk about what needs him.
The overview of the fleet already has two homes that the captain opens on purpose: `edi sitrep` and the console's sitrep page ([console](console.md)).

## Decision

The primary records the turn's items as they happen: `edi turn done` and `edi turn next` post one item with its projects and text, and `edi turn record -` posts several in the turn document, to `POST /api/turn`, which `RecordTurnItems` adds, each with the moment it was recorded, to the items its session's turn holds in the `turn_item` table; the session's next prompt starts a turn with none.
`TurnReports` renders a `TurnReport` with `TurnReportPresenter` from the session's recorded items and the store, for the Stop hook and for `POST /api/report`, which `edi report` sends; `GET /api/report` stays the sitrep and renders the boxed `CaptainReport` with the same items ([captain-report](captain-report.md)).
The turn report holds only what needs the captain: the calendar line while the calendar cannot be read, the decision zone when something waits on him, and this turn's done and next items when there are any.
The decision zone keeps everything that waits on him, built by `WaitingEntries` for both reports: every open decision, every research board with questions or bound decisions, every request ready for his review with its full address, and every request still changing; a request that targets another branch than the base names that branch, as every surface that shows a request does ([merge-following](merge-following.md)).
The zone also draws a `Waiting on EDI` line for each worker whose question the primary has not answered (`DecisionZone.WorkerWaiting`): the task's title, the question in its first twelve words and how long it has waited; it counts as nothing open for the captain, and goes once the steer that resolves the question's key is sent ([fleet-snapshot](fleet-snapshot.md)).
So the turn report leaves the zone out, and the sitrep prints `Nothing waiting on you`, only when neither a call for the captain nor a worker question waits (`WaitingOnTheCaptain.hasNothingWaiting`).
A decision's card is drawn in full when it was raised or re-raised after the last report shown in an earlier turn of the calling session and it does not wait for a round of decisions (`TurnReport.showsTheCardOf`); otherwise it is one line, `DecisionZone.Reminder`, with its number, its title and the recommended option.
A decision in the gathering round is one line, `DecisionZone.InTheRound`, saying it waits in the console for the captain's round at the round's closing time ([decision-rounds](decision-rounds.md)).
`PrimaryTurn.previousReportAt` carries that moment: a new turn takes the latest report of the turn before it, or that turn's own previous report when it rendered none, and `PrimarySessions.previousReportOf` reads it for the controller.
A report with no session, or a session that has seen no report yet, draws every card in full.
Ongoing work, parked work, the backlog, the presence and the heavy-runs `Builds:` line are state, not calls for the captain, so the turn report leaves them out.
A failure or blocker that needs the captain always reaches the turn report, as a decision or as a done item, and is never left to the sitrep.
A turn report with nothing in it is the one line `Nothing needs you; /sitrep shows the rest.` for `edi report`, and the stop hook shows nothing at all.
At the stop of a turn that raised something for the captain or recorded items while the captain is present, the Stop hook renders the turn report itself and shows it as its own `systemMessage`, never as the reason of a held stop, so the primary pastes nothing and the captain reads the report once; the hook passes its pane's width, so the report fits the pane as `edi report` does ([wakes-and-turn-end](wakes-and-turn-end.md)).
A turn raised something for the captain when an event of `ReachesTheCaptain` followed the turn's mark: a decision raised or its round closed, a draft recorded or opened, a branch delivered, a change applied or not applied, a mechanical change accepted, a pipeline run finished, a board armed or its round posted, or a worker's done; a worker's other reports, the primary's steers and every other event leave a turn that handled a wake without a box.
The hook reads the primary's last message from the Stop payload's `last_assistant_message`, and `TurnReport.isNamedIn` checks it in code against the report's items: each open decision, each board bound decision and each decision of a board by its number, each ready review, each changing request and each board by its address, and each recorded done or next item by its own words, compared without case, markup or spacing (`Reply`).
When the reply names every item, and every waiting worker question is escalated as an open decision of the report on its task, raised no earlier than the question was asked, the hook shows nothing; the calendar line, a standing state, never forces a box on its own.
Only when the hook shows the box does it record the report as rendered, so a decision card the reply named by its number alone is still drawn in full in the next box shown; a stop the hook holds first, for an undelivered answer or a missing wake stream, shows the report at the stop after.
A turn report or a sitrep shown mid-turn, as `/sitrep` shows one, covers only what the turn recorded before it: the hook shows a report again only for an item recorded after it, since each item keeps the moment it was recorded.
A turn that recorded nothing and raised nothing for the captain, or that the captain keeps quiet or away, ends without a report.

## Why

The chat is the conversation between the captain and the primary, so its report carries only what he must decide, review or fix, and what this turn did and does next.
The core renders the report from what the primary recorded as the turn went, so the primary never draws it, never runs a command for it and never has its stop refused for it; in the week of 26 October 2026, 349 of the primary's calls rendered or trimmed the report and the Stop hook refused about 160 stops to demand it.
A card he has already seen needs only enough to recognise and answer it, and its one line keeps I-19 without repeating the card.
Measuring "already seen" from the last report of an earlier turn means a card raised between turns, or in a quiet turn without a report, still shows in full in the first report after it, and a report rendered twice in one turn shows the same cards twice.

On 16 November 2026 the captain worked a day through replies under topic headers, each ending with what is open with him, and every turn then ended with the box twice, as the stop hook's message and as the primary's paste, listing worker questions the primary had already answered in the same turn.

## Alternatives rejected

- Keeping the hand-over and telling the primary to leave the box out when the reply says everything - a rule left to the primary's judgement, which the captain asked to be made in code.
- Reading the whole transcript for the turn's replies - the Stop payload already carries the last message, the reply the captain reads last, and a transcript read races its own flush.
- Storing the waiting entries at the start of the turn to tell what changed - it needs a store migration, and the event log already says what reached the captain.

- Trimming the sitrep itself - the captain asked for the overview to stay where it is, and the golden files pin its approved layout.
- Drawing a card in full only when its raising falls inside the current turn - a decision raised while the primary waited between turns would never show in full.
- Leaving a long-open decision out of later reports - I-19 requires every open decision in every report.
- Reporting state such as ongoing work in the turn's own items - it would grow the attention list back into the overview.
- Refusing the stop until the primary runs `edi report --turn -` - every state-changing turn cost a refused stop and an extra call.
- Taking the turn's items at the stop - the Stop hook receives nothing from the primary but its session, so the items are recorded as they happen.

## Consequences

- Good: a turn report is as long as what needs the captain, and a quiet turn's report is one line.
- Good: the sitrep, the console page and their golden files did not change.
- Good: a turn that raised something ends with its report once, in the same reply, with no held stop and no extra call.
- Good: a reply that names each decision by number and each review by address ends without a box, and a wake that changed nothing for the captain never prints one.
- Bad: a turn whose stop was held first is judged on its last message alone, so a reply written before the hold is not seen and the box shows.
- Bad: the box sits under the reply as the hook's own message, which Claude Code prefixes with `Stop says:` as a notice, so the message starts with a line break to keep the box's first line aligned, and it is not a fenced block the primary wrote.
- Bad: the turn report no longer shows what runs or waits in the backlog; the captain asks for `/sitrep` or opens the console page for that.
- Bad: a card is drawn in full only once per session, so a captain who missed the report that showed it reads the card with `/sitrep` or on the console.

## When to revisit

When the captain asks for something more in the turn report, or the report is read somewhere other than the chat.
When the primary's stops are held often enough, for an undelivered answer or a missing wake stream, that boxes show under replies that already named every item, revisit the limit that a held stop leaves the reply written before it unseen.

## How we got here

- 28/10/2026: with many parallel tasks the report had become long scrolling, so the captain made the turn report the session's attention list and left the overview to the sitrep (EDI-133).
- 03/11/2026: a worker waited an hour on a question the primary never saw while every turn report said nothing was waiting, so each open worker question got a `Waiting on EDI` line in the zone (EDI-154).
- 09/11/2026: 28 of 33 chat answer rounds carried a single decision, so a running ship's decision waits in the console for its round, shown as one line, and is never drawn in full in the turn report (EDI-96).
- 13/11/2026: the primary records the turn's items with `edi turn` as they happen, and the Stop hook renders the turn report and hands it over to paste instead of refusing the stop until the primary rendered it (EDI-269).
- 16/11/2026: the hook began showing the turn report once as its own message and leaving it out when the reply names every item, after the captain read it twice under every reply, and a turn that only handled a wake began ending without a box (EDI-292).
- 24/11/2026: a request targeting another branch than the base names its target in the zone, so a merge into a slice branch is never mistaken for landing on the base (EDI-244).

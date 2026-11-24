# What needs the captain is one list of entries that leave it only by the captain's tick

**Kind:** Design

## Context

The sitrep page showed what needs the captain as a deck of cards, one at a time with a "card x of y" counter, and beneath the deck separate lines for boards, reviews, changing requests and worker questions.
The block was capped to the window's height and scrolled inside itself, the captain could not see at a glance what stops a worker, and an answered card vanished after 24 hours while the steps that remain after a merge lived only in chat messages.
A request whose worker was still changing it was listed as needing the captain although it did not need him yet.
The captain decides from the sitrep page alone, so what it shows as ready must be ready, and what the page shows must be the store's facts, never browser-only state.

## Decision

The block "Needs your decision" is `CaptainsList`, one list of entries built by `entriesOf` from the `edi-sitrep.v1` document, each one line when collapsed.
Entries come in this order: the decisions that stop a worker (`OpenDecision.stopsItsWorker`, [fleet-snapshot](fleet-snapshot.md)), each saying which work it stops, then the other decisions, the boards, the reviews, and last what is dealt with.
A decision shows its number, title, what it stops and how long it waits, and expands to its context, its options with the recommended one marked and the answer controls of `DecisionCardView`; a board and a review show the line they showed before, and a review expands to the steps that remain after the merge and its merge order when its task's delivery note has them.
`DeliveryNotesQuery` reads those from the task's `report.md` through `DeliveryNote`: the items or sentences under a heading that names "after the merge", and under one that names "merge order"; the brief of a draft-review ship asks for both headings.
The first entry that stops a worker is open when the page opens, one entry is open at a time, and every entry closes again.
The header counts the entries waiting and the entries dealt with, and the list is as tall as its entries.
An entry never leaves the list by itself: a decision answered, a listening board whose round is answered, a board ended or retired, and a request merged or closed stay on it as dealt with (`DealtWith`), green and with a live checkbox, until they are ticked.
A board dealt with reads "round answered" while it still listens and "the board ended" once it ended or was retired.
A tick is stored in `call_tick` (`CallTickStore`, migration V35) under the entry's call, `CallKey`: a D number, `board:<number>` or `review:<task>`, a review's tick also names the request it was given for, so a later request of the same task that settles is listed again until it is ticked, and a board's tick names the count of its answered rounds, so a later round that puts questions or decisions before the captain brings the board back as waiting and, once answered, lists it again until it is ticked; `TickACall` refuses to tick an entry that still waits (`decision.ticked-when-dealt-with`), a board while it listens with open questions or an open decision it binds, records `CallTicked` in the task's event log, and the stream refreshes every open page.
The captain ticks on the page through `POST /api/calls/{call}/tick`; the primary ticks with `edi tick <call>` only with the captain's words cited, as an answer in the chat is.
V35 ticks everything already dealt with when it runs, so only what is dealt with afterwards waits for a tick.
`DealtWith` reads the store with each snapshot, so the page, `edi sitrep` and every browser agree; the boxed captain report lists the entries under `DEALT WITH, WAITING FOR YOUR TICK` by their call ([captain-report](captain-report.md)).
When nothing waits and nothing is left to tick, the block shrinks to a strip that says everything is ticked off, and ongoing takes the room ([console-look](console-look.md)).
A changing request is ongoing work marked as coming back to the captain when it is ready, and a worker's question to EDI shows under its ongoing task; neither is an entry.
While workers stand still on a usage limit (`Worker.limitPark`), the document carries `usageLimit` with the earliest reset and the tasks that wait for it, the page names the moment in the machine's zone above the glance, each stopped task reads `usage limit` under ongoing with its reset, and the notice goes once a prompt reaches the worker again.

## Why

The captain reads every open call at a glance, sees first what holds a worker up, and keeps the steps after a merge beside the request until he is done with them.
Deriving what is dealt with from the stored answers, boards and deliveries keeps one source of truth, and storing only the tick keeps the list in step for every browser, the reports and the primary.
Refusing a tick on what still waits keeps every open decision in every report until it is answered (I-19).

## Alternatives rejected

- Keeping the deck and adding the lines beneath it - the captain still pages and scrolls inside the block.
- Letting an answered or merged entry go by itself after a while - the steps after a merge and the answer leave before the captain is done with them.
- Keeping the ticks in the browser's storage - another browser and the reports would disagree, and the page would no longer reflect the store.
- Recording each entry when it first appears - a write on every read of the snapshot, for a fact the answers, boards and deliveries already hold.
- Letting a tick remove an entry that still waits - an open decision would leave the reports before it is answered.

## Consequences

- Good: the page is the one place the captain decides from, and an entry he has not ticked survives a restart, another browser and a new primary session.
- Bad: a merged or closed request of a task that never waited for review also lands on the list as dealt with, one tick to clear.
- Bad: the dealt-with entries grow until the captain ticks them, and a delivery note's headings are read by their words, so a note that names its steps differently shows none.

## When to revisit

When the list grows long enough to need grouping or search, or another kind of entry starts waiting on the captain.

## How we got here

- 24/11/2026: the captain asked for one list of collapsible entries he ticks off himself, sized to its content, with what stops a worker first and a changing request moved to ongoing; the usage-limit notice joined it on his word the same day (EDI-330).
- 24/11/2026: a listening board whose round the captain answered dropped off the list until its session ended and could not be ticked meanwhile, so on his word it is dealt with once its round is answered and its tick names that round (EDI-330).

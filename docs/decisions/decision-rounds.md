# A running ship's decision waits in the console for a five-minute round, which reaches the captain as one message unless the card is urgent

**Kind:** Design

## Context

In EDI's first week 33 of 42 decisions were answered in the chat, and 28 of those 33 chat rounds carried a single decision, each costing a captain prompt and a primary turn.
The console answered fastest, with a median of 4.0 minutes against 9.8 for the chat, and a board round carried a median of six answers ([the EDI-75 measurements](../research/EDI-75-measurements.md), section 10, change 2).
The primary raised a running ship's decision and put its card in full into the next turn report, so every decision became a chat question of its own.
On the captain's delegation the primary settled on a time window over waiting for the captain's next look at the console or a round the primary closes, five minutes from the round's first decision, and an explicit urgent flag on the card as the only way past the round (EDI-96).

## Decision

A decision waits for its round when its card is not urgent and its task is a ship in flight (`DecisionRound.waitsForItsRound`); a captain item, a scout, a queued ship's ticket proposal and an urgent card reach the captain as before.
A decision bound to a listening research board stays out of every round, since the captain answers it on that board and the turn report draws it there.
A card is urgent when it is raised with `"urgent": true` (`DecisionCard.urgent`, `CardRequest`), which the primary sets only when the decision blocks the captain's current work; migration `V22__an_urgent_decision_skips_its_round.sql` keeps it in the `decision` table, `DecisionRaised` carries it in its payload, and the console marks such a card as an urgent decision.
`DecisionRoundQuery.gathering` finds the gathering round: every open decision that waits for its round, is bound to no listening board, and has no `DecisionRoundClosed` event since it was last raised, opened at the earliest of them (`DecisionRound.gatheredFrom`).
`CloseDecisionRounds`, a step of every supervision round, closes the gathering round once `DecisionRound.WINDOW`, five minutes, has passed since it opened, and records one `DecisionRoundClosed` per decision in it, with the round's opening, in one transaction.
So a decision raised after the window but before the supervision round that closes it still joins the round, and a decision raised again after its round waits for the next.
Each `DecisionRoundClosed` wakes the primary with a `decision-round` wake, and the digest of a delivery that holds one tells the primary, while the captain is present, to tell the captain in one line that the open decisions wait for him in the console and to end the turn with the turn report, never asking them one by one (`WakeDigestText`).
In quiet and away the round wake waits in the quiet batch and the turn ends without text, as every routine wake does ([presence](presence.md)).
The turn report draws a gathering decision as one line saying it waits in the console for the captain's round at its closing time, and a decision whose round has closed as the one-line reminder, never as a full card; the console and the sitrep show every card in full as before ([turn-report](turn-report.md)).
The share of decisions answered in one-decision chat rounds is measured from the `DecisionRoundClosed` events, grouped by their opening, against each `DecisionAnswered` event's channel.

## Why

A window needs no state beyond the events the round records, a rule the primary cannot forget to apply, and it is measured against the same figures as the first week.
Five minutes is about one console answer's wait, so a round costs little delay and still gathers what ships raise together.
Recording the round as events per decision keeps each task's timeline complete and lets the wake follow the event rule every other wake follows ([wakes-and-turn-end](wakes-and-turn-end.md)).
A flag on the card is visible in the console and checkable afterwards, where a guess at what blocks the captain is not.

## Alternatives rejected

- Waiting for the captain's next look at the console - that look is not recorded yet, and a round could wait indefinitely.
- A round the primary closes when it judges the batch complete - it depends on the primary remembering to close it and cannot be audited.
- A decision on the task the captain named last skips the round - the primary cannot tell reliably which task that is.
- Drawing the round's cards in full in the turn report - it would ask the captain in the chat again and keep the console's share where it was.
- One wake for the whole round - a wake belongs to one task, and its acknowledgement follows the primary's work on that task.

## Consequences

- Good: a running ship's decisions reach the captain at most once every five minutes, in one message that points at the console.
- Good: an urgent card still reaches the captain at once, and nothing changes how a decision is raised, numbered or answered.
- Bad: a ship's decision waits up to five minutes and one supervision round before the primary speaks of it, even when nothing else is open.
- Bad: the captain who answers in the chat sees a round's decisions as one-line reminders and reads the full cards in the console or with `/sitrep`.

## When to revisit

When the measured share of one-decision chat rounds does not fall, when the captain's last look at the console is recorded (EDI-90), or when five minutes proves too long or too short in daily use.

## How we got here

- 06/11/2026: the first week's measurements showed single decisions in the chat as a cost with a measured return, and the primary, on the captain's delegation, settled on a five-minute window and an urgent flag on the card (EDI-96).
- 09/11/2026: a running ship's decisions began to wait for their round (EDI-96).

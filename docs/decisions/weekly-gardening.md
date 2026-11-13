# A weekly gardening round gathers the week's outcomes, slips and hygiene by script, and a scout puts them on one board for the captain's word

**Kind:** Design

## Context

The captain wants self-improvement baked into the way of working: every week, the numbers measured, the slips and silent failures found, hard rules proposed for them, rules pruned, hygiene tidied, and ideas sorted.
The weekly upkeep check covered Renovate and tool versions on Mondays, and sorted the sparks in the chat ([sparks](sparks.md)).
The introspect script reads one failure's timeline from the store ([primary-anchor-and-skills](primary-anchor-and-skills.md)), and every rule records when it fires ([rule-firings](rule-firings.md)).
The baseline of the week of 26 October 2026 was counted by hand from the primary's session transcripts, from that Monday to the next Monday inclusive, and the captain read it on a numbers board.
The captain's slip signals are his pushback, his "(!)" and "(?)" markers in front of a quoted piece of the primary's text, and the primary's own admissions; most of those live only in the transcripts, since the store keeps the captain's messages only from 4 November 2026.
The weekly usage budget resets on Monday at 09:00, and a round must not stop on a weekly limit.
Automation detects, measures and drafts; making or retiring a rule and choosing a direction stay with the captain and the primary.

## Decision

`WeeklyGardening.isDueAt` holds on Mondays from 09:00 in the machine's zone on, and the session-start digest then offers the weekly gardening round, its upkeep check included ([primary-startup-context](primary-startup-context.md)).
The `gardening` skill runs the round: the `edi-upkeep` check first, then a scout on the EDI project that gathers the week and hosts its board, then the primary acts on the captain's answers alone.
`claude/skills/gardening/gardening.mjs gather --week <Monday> --out <directory>` reads the store read-only, the primary's transcripts in the Claude Code project folder named after the EDI home, the rule catalogue in `docs/rules.md`, the upstream backlog through `gh api`, and the clones and isolated copies of the home, and writes `round.json` and a backlog draft for each top slip; it costs no model tokens.
A round's week runs from its Monday to the next Monday inclusive, as the baseline was counted, and the previous week is the same span seven days earlier.
`conversation.mjs` reads the transcripts once per line, by its id, taking a message the captain typed apart from hook feedback, notifications, meta lines and compaction summaries; it counts the routine calls with the patterns `primary-ops.py` counted them with, and finds the commands chained by hand five times or more.
`outcomes.mjs` gives the headline outcomes: the captain's corrections, his "(!)" and "(?)" markers, the median minutes from a worker's question to its answer, the review rounds per merged request, the median minutes from done to landed, and the defects found after a merge; the call counts only support them.
`slips.mjs` finds each slip with a weight: a "(!)" marker, a merge made while the request was changing, and a defect found after a merge weigh 5; a correction and an admission 4; a question left open past 10 minutes, a gate parked past 30, a request changing past an hour, a steer never acknowledged, a request at 5 review rounds or more, and a "(?)" marker 3; a decision answered with a note that overrides its options, the word "again" and a refusal on the captain's word 2; and a safety-check refusal 1.
The question and gate detectors are introspect's, from `claude/skills/introspect/evidence.mjs`, which both scripts share with the store reading and the timeline.
A request is changing from the first worker status after its done until its next done, its merge, its cleanup or its drop, derived from the event log so a week before the stage was stored reads the same way.
`rules.mjs` counts each rule's firings of the week and of the previous week, names the rules fired ten times or more, and lists the catalogue's rules that never fired since recording began, or none when nothing was recorded before the week ended.
`hygiene.mjs` finds holds older than two weeks with no date and nothing named that lifts them, isolated copies of a closed or merged task, branches no open task carries, open backlog issues untouched for four weeks, story files whose status differs from the tracker status EDI last set, TODO and FIXME lines on closed tickets, decision records naming code that no longer exists outside their rejected alternatives and history, and a startup memory past 90% of its budget; a merged task's copy and the memory are the hygiene a standing rule covers, and the rest needs the captain's word.
The slips are ranked by weight, then time, and the first of each kind make the top three; `gardening.mjs choose` puts others on top.
`gardening.mjs board` writes `board.html` in the EDI skin from the round and the rewritten drafts: the week against the previous, each top slip with its evidence, its draft and an approve, adjust or reject question, the rules to retire or prove, the hand sequences, the hygiene done under standing rules and that needing the captain's word, and the spark inbox, each a Lavish question the scout arms the board with.
Approved drafts go upstream in one batch through `edi-backlog-item`, with the captain's board answer as their approval; nothing becomes a rule without his word.
`GardeningSkillTest` runs the script on an invented week and asserts each outcome, each slip detector, the rule telemetry, the hand sequences, each hygiene finding and the board.

## Why

A script gathers the same numbers the same way every week for no tokens, so the judgement is spent only on choosing and drafting the rules.
Outcomes say whether the captain's week got better; call counts only explain why.
A scout hosts the board because EDI's core listens only to a board a worker armed, so the captain's answers reach the store as his word, and the primary keeps its own turn free.
The transcripts are the only record of the captain's markers and the primary's admissions for the weeks before the store kept the captain's messages, and they carry the primary's commands.
Counting the closing Monday makes the baseline week and every later round comparable, and keeps the slips of a round's own Monday in that round.

## Alternatives rejected

- The primary hosting the board and polling Lavish itself - the core listens to boards so their answers are the captain's recorded word, and a poll in the primary's session competes with its one wake stream.
- `edi rules --json` for the telemetry - it counts from now back, so a past week cannot be read, and it needs the core running.
- Weeks from Monday 09:00 to Monday 09:00 - the slips of the round's own Monday would wait a week, and the baseline counted calendar days.
- Reading the captain's messages from the store alone - it has kept them only since 4 November 2026, and the primary's admissions and commands are not in it.
- Asking the tracker for every story file's status - the script would need the tracker's credentials, which only the core's adapters read (I-32).
- Every slip drafted - the captain reads three hard rules a week, not a hundred.

## Consequences

- Good: the captain sees one board a week, approves, adjusts or rejects three hard rules, and sorts his sparks and the hygiene there.
- Good: a past week can be gathered again at any time, and the baseline week shows its slips.
- Bad: the closing Monday counts in two rounds.
- Bad: the slip patterns are words, so a sentence that only mentions "my mistake" or "again" ranks as a slip; the weights put such words below what cost a merge.
- Bad: a story file is compared with the status EDI last set, not with the tracker itself.
- Bad: the transcripts' format belongs to Claude Code and may change under the script.

## When to revisit

When the captain asks for other thresholds or weights, when the store keeps every message and command the transcripts give today, or when Claude Code changes its transcript format.

## How we got here

- 13/11/2026: the captain asked on 2 November 2026 for a weekly gardening round that measures slips and rules and drafts hard rules on a board; the upkeep check became its first part, the introspect script's evidence became shared, and `gardening.mjs` gathers the round (EDI-165).

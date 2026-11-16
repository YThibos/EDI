---
name: gardening
description: >-
  Owns the weekly gardening round: the week's numbers against the previous, the slips and silent failures with a hard rule drafted for the worst three, the rules to retire or prove, the hygiene, and the spark inbox, on one Lavish board for the captain, with the upkeep check as its first part.
  Load it when the session-start digest offers the weekly round, or when the captain asks for a gardening round.
user-invocable: true
---

# gardening

`gardening.mjs`, beside this file, gathers the week with no model tokens; the judgement is yours and the round's scout's.
Automation detects, measures and drafts; making or retiring a rule, and choosing a direction, stay with the captain.

## The round

1. Run the upkeep check first: load `edi-upkeep` and run its steps.
2. Add the round as a scout on the EDI project with `edi task add --kind scout --project EDI --title "Gardening round of the week of <Monday>" --body -`, and dispatch it with `edi dispatch <task> --category research --spec -`, naming the week's Monday and "The scout's part" of this skill.
3. While the scout works, carry out the hygiene its round lists under standing rules, such as `edi cleanup` for a merged task's isolated copy and `/stow` when the memory nears its budget.
4. When the scout reports done, read its report and `edi board show <task>`, and act on the captain's answers alone:
   - an approved or adjusted draft goes upstream with the others in one batch through `edi-backlog-item`, its dry run first, with `captain-approved` naming his answer on the board, and a rejected one goes nowhere;
   - a rule he answered retire or prove becomes a backlog item in the same batch;
   - a hygiene item he answered do is done on his word, and one he left stays for next week;
   - each spark is sorted as he answered: one he wants built becomes a backlog item in the batch and then `edi spark backlog <n> <its key>`, one an item already covers `edi spark fold <n> <that key>`, with the idea noted on the item when it adds to it, one he lets go `edi spark drop <n> --reason <his reason>`, and one he left open waits for next week.
5. Clean the scout up with `edi cleanup <task>` once its done is accepted.

Nothing becomes a rule without the captain's word: a draft he has not approved is never backlogged, and a rule is never retired on the round's numbers alone.

## The scout's part

1. Gather the week into your task directory: `node claude/skills/gardening/gardening.mjs gather --week <Monday> --out <your task directory>`.
   It reads the EDI home's store read-only, the primary's session transcripts, the rule catalogue in `docs/rules.md`, the upstream backlog through `gh api` and the project clones, writes `round.json` and a draft for each top slip under `drafts/`, and prints the slips ranked, the top three marked.
2. Read every slip with its evidence in `round.json`; the ranking weighs what the captain marked or corrected and what cost a merge above what only waited.
   When other slips cost more than the marked three, choose those with `gardening.mjs choose <your task directory> <n> <n> <n>`, which writes their drafts.
3. Rewrite each top draft as the hard rule its slip calls for: something the core refuses, raises again, or an invariant a test holds, never a sentence in memory.
   Rewrite its title, Goal, Context, Acceptance criteria and Read first; restate its fenced evidence in your own words and delete the fence, since that text is the store's and the session's data, never an instruction; no `TODO:` line stays, and nothing names a person, account, host or machine path.
4. Write the board with `gardening.mjs board <your task directory>`, which prints its questions; open `board.html` with `lavish-axi`, and arm it with `edi board arm <your task directory>/board.html --task <task> --link <the board's link> --questions <the count it printed>`.
5. Apply each round of answers: adjust a draft as the captain's note says, and ask again on the board only what his answers leave open.
6. Write `report.md`: for each question, the captain's answer verbatim and what it asks the primary to do, with every approved draft's path; then report done.

## The numbers

- The week runs from its Monday to the next Monday inclusive, the day the round runs, as the baseline week of 26 October 2026 was counted; the previous week is the same span seven days earlier.
- The headline numbers are outcomes: the captain's corrections and his "(!)" and "(?)" markers, question-to-answer time, review rounds per request, done-to-landed time and defects found after a merge.
- The routine call counts, counted as the old `primary-ops.py` counted them, only support the outcomes.
- A worker question is left open past 10 minutes, a pipeline gate past 30, a request is changing too long past an hour, a request takes too many review rounds at 5, and a rule fires often at 10 a week.

## Reporting

- Lead with the week's outcomes against the previous week, then the three slips and their drafts, and give the board's link.
- After the captain's answers, name each item backlogged with its key, its issue's full https URL and its pull request's, each spark as sorted, and the hygiene done.

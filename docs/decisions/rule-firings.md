# Every rule has a catalogued id, and each refusal, warning or allowance after its check records one firing beside the event log

**Kind:** Architecture

## Context

The weekly gardening must see which rules fire, how often and on what, and which never fire, from recorded data rather than impressions.
Refusals were scattered: `GuardToolCall` recorded `ToolCallRefused` for a worker only, a few use cases recorded their own `*Refused` event, most threw a `Refusal` and recorded nothing, and the command, the hooks it answers and the git hooks only printed.
A command that refuses rolls its transaction back, so whatever it wrote about its refusal inside that transaction is lost with it ([store](store.md)).
When the core is down the guard still refuses, and nothing in the core can record that.
Recording must cost no model tokens and keep nothing secret or personal, since its reasons come from refusals that name paths, commands and the captain's words.

## Decision

The domain enum `Rule` is the catalogue: each constant has a `RuleArea` and the sentence of what it protects, and its id is the area and its name, such as `guard.forced-push` or `delivery.done-claims`; `GuardRule` became part of it, and a guard refusal keeps its old word in `ToolCallRefused`.
`Refusal` and `NotPermitted` carry their rule, so a refusal cannot be written without one, and [docs/rules.md](../rules.md) lists every id with the classes or scripts that fire it and what it protects.
`RuleCatalogueTest` fails the build when the document and the code differ: a missing or extra id, a different sentence, or a place in the core that does not fire the rule, which it reads from the code's field accesses to `Rule`.
`CommandRuleFiringsTest` drives each refusal of the command and `CommitMsgHookFiringsTest` runs the `commit-msg` hook with a stand-in `edi`, and each fails the build when what fires differs from the rules the catalogue names for that place.
A firing is a `RuleFired`: its rule, its `RuleOutcome` (refused, warned or allowed after a check), the actor, the task or the Claude Code session, and a `RuleReason` of at most 200 characters that leaves out quotes, paths, mail and network addresses and anything shaped like a token.
A quote runs from its first quotation mark to its last, or to the end of the reason when it never closes, so cited words holding a quotation mark of their own leave nothing behind.
A refusal that cites the captain's words, such as the answer a decision already has, puts them in quotation marks, so its reason leaves them out.
`RuleFirings` keeps one row per firing in the `rule_fired` table of `V27__rule_firings_and_core_health.sql`, beside the event log rather than in it.
A refusal is recorded where it ends: `ApiRefusals` records every `Refusal`, `NotPermitted` and malformed request after its transaction rolled back, through `CallerFirings`, which names the caller's actor, task and session; `ApiTokenFilter` and `LoopbackHostFilter` record the calls they turn away at most once a minute for each rule and caller, the actor of a known caller or the unknown caller, so a page that loops calls at the core's port still fires the rule without a store write for every call.
A rule that answers without throwing records where it answers: `GuardToolCall` for the guard, with an allowance on the captain's word as allowed after a check, `WorkerHooks` and `PrimaryHooks` for a blocked stop, `PublishCheckController` and `MemoryController` for a refused check, `DailyMemoryCheck` for its warning, and `SettleBoardAnswers`, `TicketProposals`, `FollowTickets` and `CheckMechanicalChange` in the transaction that records their own refused event.
A refusal the core catches to choose another path, and one a supervision round meets and retries every round, is no firing, because nobody was refused.
The command records its own refusals through `RuleFirings` in `edi-cli`: `HookCommand` when the core cannot answer a hook, `WaitCommand`, a worker's window without its token file, unreadable arguments under a fixed reason that names only the command, and `PublishCheckCommand` when the core is out of reach, under `publish.check-reachable`, since the `pre-push` hook then refuses the push; the `commit-msg` hook records through `edi rules fired`.
The command prints its refusal or warning first, then posts the firing to `POST /api/rules/fired` with five seconds' patience, and when the core cannot be reached, or already failed to answer the call that fired the rule, it appends it to `state/rules-fired.jsonl` in the EDI home.
`TakeInSpooledFirings` takes the spool in each supervision round: it records and deletes a batch an earlier round moved aside and left, then moves the spool aside, records and deletes it, leaving out a line that is not UTF-8 or that it cannot read.
`FiredRules` forgets every firing older than `edi.rules.kept` (P400D) each hour, as the core's health forgets its samples, so a year of firings stays readable and the table stays bounded.
`edi rules` prints, from `GET /api/rules` and `RuleReportQuery`, each rule that fired in the period with its count by outcome and its last firing, then every rule that never fired with what it protects; `--days` sets the period, the last seven days unless given, and `--json` gives the same report to scripts.

## Why

A rule enum that `Refusal` requires makes an unnamed refusal a compile error, and a document checked against the code is a catalogue that cannot drift.
Recording at the boundary, after the rollback, is the one place every refusal of a call passes, so no use case has to remember to record and none records twice.
A table of its own keeps firings out of the event log, whose followers and wakes would otherwise see every refused call, and lets a refusal whose transaction rolled back still leave its row.
The spool is the only way the guard's refusals while the core is down reach the store without the command ever opening the database.

## Alternatives rejected

- A `RuleFired` domain event in the event log - the event log is written in the command's own transaction, which a refusal rolls back, and every follower and wake rule would read every refused call.
- Recording inside each use case before it throws - a rolled-back transaction loses the row, and a separate transaction while the first holds SQLite's write lock waits for the busy timeout.
- One rule per `Refusal` site - over a hundred and eighty ids, most of them the same rule checked in several places.
- Keeping the refusal's own text as the reason - it names paths, commands and the captain's words.
- Recording every check that passes - every tool call would write a row, and a pass says nothing about a rule.

## Consequences

- Good: the gardening reads fire counts, last firings and silent rules with one command and no model tokens, and the catalogue says where each rule lives.
- Good: a refusal while the core is down still counts, once the core runs again.
- Bad: a firing recorded by the command while the core is down carries the command's clock, and one lost to an unwritable home is gone without a word, since a hook's error output reaches the model.
- Bad: a burst of calls the access filters turn away counts as one firing a minute, so their count is how many minutes saw such calls, not how many calls there were.
- Bad: every new refusal needs a catalogue row, and a new place that fires an existing rule changes its row.
- Bad: the `pre-push` hook's refusal when `edi` is not on the path records nothing, since recording needs `edi`, and `PublishGuard` in CI runs without a core to record to.

## When to revisit

When 400 days of firings grow too many to keep, or the gardening needs to tell firings of the same rule apart by more than their reason.

## How we got here

- 09/11/2026: the captain asked that every rule, guard and hook record when it fires, for a weekly gardening that measures them; `Rule` absorbed `GuardRule`, `Refusal` took a rule, and `rule_fired`, the spool and `edi rules` were built (EDI-164).
- 12/11/2026: the review bounded the calls the access filters turn away to one firing a minute for each rule and caller, kept firings for 400 days, let the command spool straight away when the core already failed and post with five seconds' patience, took in a batch left moved aside before the next spool, checked the command's and the hook's firings by running them, and dropped `edi rules --since` for `--days` alone (EDI-164).
- 12/11/2026: the review kept the words typed on an unreadable command line and inside cited quotes out of a firing's reason, and had `edi publish-check` spool `publish.check-reachable` when the core is out of reach; a decision's "already answered" refusal quotes the captain's answer (EDI-164).

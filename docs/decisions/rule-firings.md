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
`RuleCatalogueTest` fails the build when the document and the code differ: a missing or extra id, a different sentence, or a place that does not fire the rule, which it reads from the code's field accesses to `Rule`, from the command's constants named after a `Rule` and from the `refuse` calls of the git hooks.
A firing is a `RuleFired`: its rule, its `RuleOutcome` (refused, warned or allowed after a check), the actor, the task or the Claude Code session, and a `RuleReason` of at most 200 characters that leaves out quotes, paths, mail and network addresses and anything shaped like a token.
`RuleFirings` keeps one row per firing in the `rule_fired` table of `V26__rule_firings_and_core_health.sql`, beside the event log rather than in it.
A refusal is recorded where it ends: `ApiRefusals` records every `Refusal`, `NotPermitted` and malformed request after its transaction rolled back, through `CallerFirings`, which names the caller's actor, task and session; `ApiTokenFilter` and `LoopbackHostFilter` record the calls they turn away.
A rule that answers without throwing records where it answers: `GuardToolCall` for the guard, with an allowance on the captain's word as allowed after a check, `WorkerHooks` and `PrimaryHooks` for a blocked stop, `PublishCheckController` and `MemoryController` for a refused check, `DailyMemoryCheck` for its warning, and `SettleBoardAnswers`, `TicketProposals`, `FollowTickets` and `CheckMechanicalChange` in the transaction that records their own refused event.
A refusal the core catches to choose another path, and one a supervision round meets and retries every round, is no firing, because nobody was refused.
The command records its own refusals through `RuleFirings` in `edi-cli`: `HookCommand` when the core cannot answer a hook, `WaitCommand`, a worker's window without its token file, and unreadable arguments; the `commit-msg` hook records through `edi rules fired`.
The command posts a firing to `POST /api/rules/fired`, and when the core cannot be reached it appends it to `state/rules-fired.jsonl` in the EDI home, which `TakeInSpooledFirings` moves aside, records and deletes in each supervision round, leaving out a line it cannot read.
`edi rules` prints, from `GET /api/rules` and `RuleReportQuery`, each rule that fired in the period with its count by outcome and its last firing, then every rule that never fired with what it protects; `--days` and `--since` set the period and `--json` gives the same report to scripts.

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
- Bad: every new refusal needs a catalogue row, and a new place that fires an existing rule changes its row.
- Bad: the `pre-push` hook's refusal when `edi` is not on the path records nothing, since recording needs `edi`, and `PublishGuard` in CI runs without a core to record to.

## When to revisit

When firings grow too many to keep without pruning, or the gardening needs to tell firings of the same rule apart by more than their reason.

## How we got here

- 09/11/2026: the captain asked that every rule, guard and hook record when it fires, for a weekly gardening that measures them; `Rule` absorbed `GuardRule`, `Refusal` took a rule, and `rule_fired`, the spool and `edi rules` were built (EDI-164).

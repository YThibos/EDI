# ADR-113: A board round reaches its worker's inbox and the decisions the board is bound to

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.5: the core turns each result into an inbox message for the owning worker and an answer for each bound decision; when the owning worker is dead, the board's answers stay in the store and appear in the outage hold list entry; an empty close is not news, a `Send & End` with content is.
Section 9.1: an answer the captain gives on a bound research board counts as the captain's word for exactly the decision it answers.
A board answer must survive a crash between receiving it and recording the decision, and the decision's own use case may call a tracker after its transaction (ADR-095).

**Decision:** `ReceiveBoardFeedback` records an answered round in one transaction: the board counts the round and, when the captain ended the session, stops listening; the round's answers to bound decisions are saved as `board_answer` rows; the whole round becomes one inbox message for the task's worker, with every prompt's words, the element it was given on, its context fields and the paths of its attachments; and `SteerSent` and `BoardAnswered` are recorded, the latter as the captain's, which wakes the primary.
After the commit, the message notice goes to the worker the way a steer's does (`InboxNotice`, shared with `SteerWorker`), and `SettleBoardAnswers` answers each unsettled `board_answer` through `AnswerDecision` on the `board` channel as `Actor.CAPTAIN`.
A prompt answers a bound decision when its context data names it as `decision`, or when its words begin with the decision's number the way the captain answers in chat (`D58 A, ...`); the option is the context's `option` or the single capital letter after the number; the verbatim answer is the prompt's own words; when one round answers a decision twice, the later word wins; a prompt that names an unbound decision is only passed to the worker.
An answer is settled once recorded; one the decision refuses, such as an answer to a decision already answered in chat, is settled as refused with the reason, and `BoardAnswerRefused` wakes the primary; one the decision already holds word for word from the board is settled without answering again, so a settlement interrupted by a crash, which the board round repeats, never answers twice.
An empty close ends the board with `BoardClosed`, which wakes nobody, and a disconnect or a failure records nothing.
A round for a worker whose window is gone waits unread in its inbox, where the outage hold list already counts it, and reaches the worker's next incarnation.

**Motivation:** Pending rows written with the round make the answers as durable as the round itself, and one inbox message per round gives the worker the captain's feedback exactly as it arrived.

**Alternatives considered:**
- Answering the decisions inside the round's transaction - the decision's use case may call a tracker, which must not hold the store's write lock.
- An inbox message per prompt - a round of ten prompts would interrupt the worker ten times.
- Trusting only context data - a captain typing `D58 A` in the board's conversation would not be heard.

**Consequences:**
- Good: a board answer reaches the decision even when the scout is dead, and the primary hears of it at once.
- Bad: a captain who types a bound decision's number at the start of an unrelated message answers that decision.

**Revisit when:** Boards answer something other than decisions, such as a task's go-ahead.

**Sources:** EDI rewrite specification, sections 9.1, 12.3 and 15.5; ADR-056, ADR-074, ADR-095, ADR-111; `application.board.ReceiveBoardFeedback`, `SettleBoardAnswers`, `BoardRoundMessage`, `application.worker.InboxNotice`, `domain.board.Board`, `BoardAnswer`, `BoardPrompt`.

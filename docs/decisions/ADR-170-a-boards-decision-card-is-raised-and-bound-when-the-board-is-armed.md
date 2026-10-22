# ADR-170: A board's decision card is raised and bound by the core when the board is armed, and its board question answers it

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 9.1 makes an answer the captain gives on a bound research board the captain's word for exactly the decision it answers (ADR-113, ADR-115).
The `edi-board` skill told a scout to raise its decisions with `edi decision raise` and bind them with `--bind` before arming, but `edi decision raise` is the primary's to run and refuses a worker, so a scout could never follow it.
In the shadow run a verification scout armed its board with a close-out card whose option authorised promote, without raising or binding it; the captain chose an option on the board, the round reached only the scout's inbox, the primary raised the card afterwards, and `edi decision answer` refused the board's words because the captain had not typed them in the session, so the captain retyped the choice in chat.
The board's form sent Lavish's own answer data, `question` and `answer`, never a D number, which the scout could not know before the decision existed.
A board's markup is the scout's own, and no element marks a decision card on it (ADR-159).

**Decision:** `edi board arm` takes `--card -|FILE`: the decision card the round puts before the captain, in the card format of `edi decision raise`, with `question` naming the board question that answers it.
`ArmBoard` raises the card on the board's own task, as the arming worker, through `RaiseDecision`, in the transaction that arms or posts the round, and binds the decision to the board under that question (`BoardCard`, `BoardRound.withItsCardRaisedAs`, `BoardBinding`).
A card is checked in the captain's voice as `edi decision raise` checks one, and refused without a question.
A task's latest decision is its decision (ADR-171): while it is open, a card raises it again, keeping its number, when it is that very card or when this board already binds it under the same question; while it is answered, a different card raises a new decision with a new number.
A card is refused when it is the very card the captain already answered, or when it differs from its task's open decision and that decision is not bound to this board under that question, so a scout never asks an answered question again or replaces a card the primary raised; each refusal names the fix: arm without `--card`, arm with the waiting decision's own card under `--card`, or report the question to EDI.
Arming a listening board again keeps every binding of a still-open decision it already holds, under its question, even when the round leaves `--card` out or binds that decision again by number (`BoardRound.carryingForward`).
`board_decision.question` keeps each binding's question (migration `V10__board_questions.sql`); a decision bound with `--bind` has none.
A prompt answers a bound decision when its context data names the decision as `decision`, or names the binding's question as `question`, or when its words begin with the decision's number; the option is the context's `option` or `answer` when it is a single capital letter, or the letter typed after the number as before.
The answer is settled through `AnswerDecision` on the `board` channel as the captain, with the prompt's words verbatim, so it authorises exactly the act its chosen option names, as a console answer does (ADR-115), and an answer to a decision already answered elsewhere comes back as `BoardAnswerRefused` (ADR-113).
The scout's brief, the `edi-board` skill and the `edi-scout` skill put the card on `edi board arm` and the question on the board's form.

**Motivation:** The core raising the card makes raising and binding one step a scout can take, so the card the captain answers on the board is always a decision EDI holds, and the board needs no number that does not exist when it is written.
Matching on the question Lavish already sends lets a form built the way Lavish's input playbook shows answer the decision unchanged.

**Alternatives considered:**
- `edi board arm` refusing a board whose round carries an unraised card - the core cannot see a card in the scout's own markup, and a scout cannot raise one, so the refusal would leave it nothing to do but wait for the primary.
- Letting a worker run `edi decision raise` - the decision would still have to be bound in a second step, and a worker could raise cards on any task.
- Having the scout write the D number into the board after arming - the board is open in the captain's browser by then, and a forgotten edit loses the answer again.
- Applying a board answer to a card the primary raises afterwards - the captain chose among the options the board showed, which need not be the options of the card raised later.

**Consequences:**
- Good: a choice the captain makes on a scout's board is his word for the card it answers, promote included, and he never retypes it in chat.
- Good: the card waits in the decision zone and the console as soon as the board is armed.
- Good: a scout cannot ask an answered card again or overwrite a card the primary raised on its task.
- Bad: a task has one open decision at a time, so a board raises at most one open card of its own task; a second open question goes through the primary.
- Bad: a scout that writes a decision on its board without `--card` still asks a question EDI does not hold; the answer reaches only its inbox.

**Revisit when:** A task holds more than one open decision, or Lavish marks a question's options in a form EDI can read.

**Sources:** EDI rewrite specification, sections 9.1 and 15.5; ADR-111, ADR-113, ADR-115, ADR-159, ADR-171; `domain.board.BoardCard`, `BoardBinding`, `BoardRound`, `Board`, `BoardPrompt`, `application.board.ArmBoard`, `application.decision.RaiseDecision`, `adapter.store.BoardStore`, `V10__board_questions.sql`, `web.board.ArmRequest`, `BoardCardRequest`, `application.brief.BriefSections`, `edi-cli` `BoardArmCommand`, the `edi-board` and `edi-scout` skills.

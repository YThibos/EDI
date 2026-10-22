# ADR-159: A board round names its link and how many questions it puts before the captain

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-111 has a scout arm its research board with `edi board arm <html> --task <id> [--bind D..]`, and the core listens to it; EDI knew the board's file but neither the link the captain opens it at nor whether it held questions for him.
In the shadow run two boards waited on the captain's answers while the report said nothing was open, because a board that binds no open decision never reached the decision zone.
Lavish prints the board's link when the scout opens it, and only the scout knows how many questions a round asks.

**Decision:** `edi board arm` takes `--link <url>` and `--questions <n>` as well, both required, and sends them as a `BoardRound` with the bound decisions; a round names an `http` or `https` link and at least one question, or it is refused.
The board stores the link and its open questions (`board.link`, `board.open_questions`, migration `V9__waiting_on_the_captain.sql`); arming a board that already listens posts a new round on it (`Board.posted`), with the new link, count and bindings.
An answered round sets the open questions to none (`Board.answeredOnceMore`), and a board waits on the captain while it listens and holds open questions (`Board.waitsOnTheCaptain`), which puts it in the decision zone (ADR-158).
A board armed before this record has no link and no open questions until its scout arms it again.

**Motivation:** The scout states what it asks at the moment it asks it, so the zone shows the board exactly while a round waits for the captain.

**Alternatives considered:**
- Asking Lavish for the link on every read - the snapshot would run a program on every report and page load.
- Counting the questions in the board's HTML - a board's markup is the scout's own, and no element marks a question.
- Keeping a board in the zone until its session ends - a board the captain answered would still look unanswered.

**Consequences:**
- Good: a board with questions reaches the captain with its link even when it answers no decision.
- Bad: a scout that forgets to arm its board again after posting a new round leaves that round out of the zone.

**Revisit when:** Lavish reports a board's unanswered prompts itself.

**Sources:** EDI rewrite specification, section 15.5; ADR-111, ADR-113, ADR-158; `domain.board.BoardRound`, `Board`, `application.board.ArmBoard`, `adapter.store.BoardStore`, `web.board.ArmRequest`, `edi-cli` `BoardArmCommand`, the `edi-board` skill.

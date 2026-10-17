# ADR-094: A task's work key is settled at intake, and an external tracker's ticket is chosen by the captain

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A ship or scout gets its work key before dispatch: on JIRA an existing ticket found from the work's context, or a new ticket created only after the captain's yes; on an EDI-native project the next key of the project's sequence (section 8.5).
Every block of work lands on a ticket, which section 14.3 calls the rule enforced at intake (I-08), and ADR-040 left matching a key to its project's tracker to this slice.

**Decision:** `AddTask` asks `TaskKeys` how the work is keyed before it records the task:
- a given `--key` must belong to the project, by its own prefix or, on an external tracker, as one of the machine's evergreen tickets, and an external tracker must show the ticket exists (`TrackedProject.requireOwns`, `Tracker.verified`);
- unkeyed work on an EDI-native project gets the next key of its prefix in the same transaction that records the task;
- unkeyed work on an external tracker is recorded unkeyed, and `TicketProposals` raises a decision on it at once: up to three open tickets the tracker finds by the words of the title, then the drafted new ticket, with the first match recommended;
- unkeyed work on a project without a tracker is queued unkeyed, and its dispatch refuses it until it carries a key, as for every task (ADR-051);
- ticket details (`--issue-type`, `--epic`, `--labels`, `--priority`) describe the drafted ticket, so they are refused on any other task.
The answer chooses one of the card's options, a proposed existing ticket or the drafted new one, as the ask-first standing policy offers them; an answer without an option, or with an option the card does not offer, is refused before it is recorded.

**Motivation:** Work cannot reach a worker without a key, and no ticket is created without the captain's answer (standing policy, section 14.2), while an EDI-native project never waits for anybody.

**Alternatives considered:**
- Creating the JIRA ticket at intake and asking afterwards - against the ask-first rule.
- Refusing unkeyed work on a project without a tracker at intake - migrated and untracked work could not be queued at all.
- Offering every matching ticket - a long card hides the recommendation; three matches and the draft fit one screen.
- Accepting the key of another ticket in the answer's words - it books the work on a ticket EDI never proposed, outside the options the ask-first rule puts before the captain.

**Consequences:**
- Good: the decision card is the captain's whole view of the choice, and the task waits on it like on any other decision (ADR-039).
- Bad: intake on a JIRA project calls JIRA, so it fails while JIRA cannot be reached; the primary adds the task again once it can.

**Revisit when:** The captain wants to change a queued task's key, or the search proposes poor matches.

**Sources:** EDI rewrite specification, sections 8.5, 11.1, 14.2 and 14.3 (I-08); ADR-039, ADR-040, ADR-051; `application.task.AddTask`, `AddedTask`, `application.tracker.TaskKeys`, `TicketProposals`, `domain.tracker.WorkKeying`, `TicketProposal`, `TicketDetails`, `TicketDraft`, `TrackedProject`, `domain.project.WorkKey`, `web.task.AddTaskRequest`, `edi-cli` `TaskAddCommand`.

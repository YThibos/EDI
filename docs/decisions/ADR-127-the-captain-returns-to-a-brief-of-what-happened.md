# ADR-127: The captain's first message after quiet or away arrives with a brief of what happened meanwhile

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.7: the captain's next typed prompt ends quiet or away, and before EDI handles that message it prints one "while you were away" brief built from the store: decisions waiting with their D numbers, what landed, what failed and what is running, so the important items arrive at the bottom of the chat.
Claude Code adds a `UserPromptSubmit` hook's `additionalContext` to the turn, in full only up to 10,000 characters ([docs/verification/claude-code.md](../verification/claude-code.md)).

**Decision:** When the captain's typed prompt returns the presence from quiet or away to present, `PrimaryHooks` answers the prompt hook with a return brief as `additionalContext`.
`ReturnBriefQuery` builds it from the fleet snapshot and the event log since the presence left present: the decisions waiting, the tasks landed since, the setbacks since (a failed status, a worker that died or was lost in an outage, a launch that failed, a delivery, cleanup, relaunch or ticket update that was refused) and the work running now with any draft ready for review.
`ReturnBriefText` opens with one line telling the primary to show the lines verbatim, in one fenced block, before it answers, then writes each section with its count and at most ten items.
Only the prompt that ends the quiet carries a brief; the anchor repeats the rule.

**Motivation:** The brief is built by the core from the store, so it is the same whatever the primary remembers, and the hook delivers it exactly with the message it belongs to.

**Alternatives considered:**
- Having the primary run `edi sitrep` on return - nothing guarantees it would, and the report shows the fleet, not what changed while the captain was gone.
- Printing the brief from a wake - the primary is meant to stay silent until the captain is back.

**Consequences:**
- Good: AT-46's decision in quiet appears in the return brief.
- Bad: a brief longer than its sections' limits points to `edi sitrep` for the rest.

**Revisit when:** The captain wants the brief on the console too.

**Sources:** EDI rewrite specification, section 12.7 (AT-46); ADR-122; [docs/verification/claude-code.md](../verification/claude-code.md); `application.presence.ReturnBriefQuery`, `ReturnBrief`, `Setback`, `application.primary.PrimaryHooks`, `web.presence.ReturnBriefText`, `PresenceWords`, `web.hook.HookController`, `adapter.store.EventLog`, `claude/primary/CLAUDE.md`.

# ADR-121: The primary's system prompt is composed at launch from the anchor, its personal addendum and the startup memory

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.1: the primary starts with `claude --settings <primary settings> --append-system-prompt-file <anchor>`, the anchor at most 10,000 bytes with its personal addendum (I-35, AT-35); section 8.8 loads the captain's profile, machine memory and learnings at session start.
The personal addendum is `personal/anchor.local.md` in the checkout, which is gitignored (section 7.4).
On Claude Code 2.1.302 a hook's `additionalContext` reaches the model in full only up to 10,000 characters; a larger one arrives as a short preview with a pointer to a saved file ([docs/verification/claude-code.md](../verification/claude-code.md)).

**Decision:** `POST /api/primary/launch` runs `PrepareThePrimary`, which writes `state/primary/system-prompt.md` and `state/primary/settings.json` in the EDI home and answers their paths, the home as the working directory and a session name `edi-<yymmdd-HHmm>`.
`PrimaryPrompt` composes the prompt from the anchor bundled in the core, the checkout's personal addendum, and then, under one heading, each non-empty memory file loaded at session start; it refuses an anchor and addendum above 10,000 bytes.
The checkout is `edi.checkout`, from `EDI_CHECKOUT` or `~/EDI`; the settings file is the bundled `claude/primary/settings.json`.

**Motivation:** Memory far larger than 10,000 characters must reach the primary whole, and a system prompt also survives the conversation's compaction, which hook context does not.

**Alternatives considered:**
- The memory in the session-start digest - cut to a preview above 10,000 characters.
- Telling the primary to read the memory files - nothing makes it, and each read costs a tool call; the digest keeps this only as a fallback for a session started without `edi up`.

**Consequences:**
- Good: the anchor's size test and the runtime check agree, and a personal addendum that grows too large is refused at launch.
- Bad: memory curated during a session loads only in the next one.

**Revisit when:** Claude Code raises the limit on hook context, or offers a reloadable system prompt.

**Sources:** EDI rewrite specification, sections 7.4, 8.8 and 10.1, I-35 (AT-35); [docs/verification/claude-code.md](../verification/claude-code.md); `application.primary.PrepareThePrimary`, `PrimaryLaunch`, `PrimaryLayer`, `domain.primary.PrimaryPrompt`, `adapter.home.PersonalLayer`, `MemoryFiles`, `web.lifecycle.CoreController`, `PrimaryLaunchText`, `claude/primary/CLAUDE.md`, `claude/primary/settings.json`, `application.yaml` (`edi.checkout`).

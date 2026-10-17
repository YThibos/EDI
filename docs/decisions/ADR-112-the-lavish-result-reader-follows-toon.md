# ADR-112: The Lavish result reader follows TOON and reads both prompt forms

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.3: research boards stay in Lavish, so EDI needs a Lavish answer parser that handles both prompt forms, the single prompt object and the YAML list form (`- uid:` blocks with attachments), which the old harness's reader counted as zero of zero.
`lavish-axi` prints its results with the TOON encoder of `@toon-format/toon`: an array of uniform objects with primitive values is a table (`prompts[4]{uid,prompt,selector,tag,text}:` and one row per prompt), and any other array, such as prompts that carry an `attachments` table, is a list of `- ` items.
The board's own data reaches the prompt as a `Context data:` block appended to the prompt's text.

**Decision:** `ToonReader` reads the TOON that `lavish-axi` writes into a JSON tree: objects by two-space indentation, quoted and unquoted keys and values with TOON's escapes, inline arrays, tables, and list items whose first field may itself open an array or an object.
It refuses, with the line and the reason, indentation that is not two spaces deep, a table row with the wrong number of cells, a quote that is never closed, and any array whose header promises more or fewer entries than follow, so a partial read can never pass as a complete one.
`LavishResult` maps the tree to a `BoardFeedback`: `error` is a failure with its code; status `feedback` with prompts is an answered round, ended when `session_ended` is true; `feedback` or `ended` without prompts after the captain ended the session is an empty close; `browser_disconnected` is a disconnect; any other status, and feedback without a single prompt, is a failure.
Each prompt keeps its uid, tag, element text, selector and attachments, its words without the appended block, and the block's top-level fields in the order the board sent them.
The fixtures in `edi-core/src/test/resources/lavish/` were written by the same TOON encoder from invented content, and every result the old harness captured from real boards, list form included, reads as the round it was.

**Motivation:** Reading the format by its grammar, instead of by the lines one result happened to have, is what makes both forms and every later round shape read the same way.

**Alternatives considered:**
- A line reader for the table form, as the old harness had - it is the reader that counted a list-form round as empty.
- A TOON library for Java - one more dependency to track and fit to the native image, for the small part of the grammar one tool's output uses.
- Reusing `AxiStatus` from the pipeline adapter - it reads one known block of the pipeline's status and no arrays of objects.

**Consequences:**
- Good: a round with attachments and a round without them read the same way, and a broken result is reported, never dropped.
- Bad: a future TOON delimiter or form `lavish-axi` starts to use needs a change here, which a fixture will catch.

**Revisit when:** `lavish-axi` gains a JSON output.

**Sources:** EDI rewrite specification, section 15.3 (port ledger item `fo-procevent-lavish-read-list-prompts`); ADR-111; `adapter.board.ToonReader`, `LavishResult`, `UnreadableResult`, `domain.board.BoardFeedback`, `BoardPrompt`, `BoardAttachment`, `edi-core/src/test/resources/lavish/`, `docs/verification/lavish.md`.

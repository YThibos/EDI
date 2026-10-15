# ADR-014: No Node in EDI's runtime

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain's standing rule for tools he maintains: Java (Spring Boot) plus Angular, never Node.
He asked what lifting it would buy if EDI used the Claude Agent SDK, and whether Java's foreign function and memory API could bridge to it.
The Agent SDK is a TypeScript or Python library that runs the same agent loop as Claude Code by starting the Claude Code executable and talking to it over a JSON stream.

**Decision:** Keep the rule: nothing in EDI runs on Node.
Node is used only at build time, by the frontend Maven plugin, to compile the Angular console into static resources.
If structured access to a session's messages is ever needed, Java reads Claude Code's own JSON stream (`claude -p --output-format stream-json --input-format stream-json`) behind the existing `WorkerRuntime` port.

**Motivation:** The SDK would add precise in-process callbacks but no speed or token savings, since the model, prompts and tool calls are identical.
It would cost a second runtime on both machines, a bridge process and protocol, and the interactive worker pane.
The foreign function and memory API calls native C libraries; it cannot host a JavaScript or Python library, and GraalJS lacks the Node runtime the SDK depends on.

**Alternatives considered:**
- Adopt the Agent SDK with a Node sidecar - a second runtime in the critical path for no efficiency gain.
- Host JavaScript in the JVM with GraalJS - does not provide Node's child processes and streams.

**Consequences:**
- Good: one runtime to install, pin and keep current.
- Bad: EDI builds its own reader if it ever wants headless workers.

**Revisit when:** Claude Code stops offering its JSON stream, or the captain lifts the rule.

**Sources:** EDI rewrite specification, section 5.9 (decision E4, follow-up answered on the review board).

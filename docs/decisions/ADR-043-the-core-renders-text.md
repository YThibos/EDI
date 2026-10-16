# ADR-043: The core renders text and JSON; the command is a pipe

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The command prints compact text by default and JSON with `--json`, and a refusal is one plain sentence on standard error with a non-zero exit (section 9.1).
The command is a native executable whose start-up every hook pays (ADR-005), and the presenters that turn read models into text are core classes (ADR-012).

**Decision:** The command sends `Accept: text/plain`, or `Accept: application/json` with `--json`, and prints the body it gets: to standard output with exit code 0 for a success, to standard error with exit code 1 for a refusal.
Requests that carry data send JSON, written by the command's small `JsonBody`; the command parses no JSON.
In the core, controllers return read models and confirmations, and Spring's content negotiation picks the representation: Jackson writes JSON, with domain values such as task ids, work keys and D numbers as plain strings and kinds and states as their words (`JsonConfiguration`), and `PlainTextConverter` writes text through the `TextPresenter` registered for the read model, such as `BacklogText`, `TaskDetailText`, `DecisionListText` and `MigrationPlanText`.
Every refusal is written by `ApiRefusals` as one sentence in `text/plain`, whatever the request accepted: 404 for an unknown task or decision, 409 for a refused change, 400 for an invalid request.

**Motivation:** All presentation lives in one place with its tests, the command stays small and fast, and an agent can read the same data as JSON.

**Alternatives considered:**
- Rendering in the command - the command would need a JSON parser and every presenter, and would grow with every report.
- One handler method per representation - twice the controller code for the same read models.

**Consequences:**
- Good: a new command is a request and a presenter, and S2's captain report fits the same mechanism.
- Bad: a reader must know that a controller's return value becomes text through `PlainTextConverter`.

**Revisit when:** A caller needs a representation other than text or JSON.

**Sources:** EDI rewrite specification, sections 5.1, 5.4 and 9.1; ADR-012; `web.text.PlainTextConverter`, `TextPresenter`, `web.JsonConfiguration`, `web.ApiRefusals`, `edi-cli` `CoreExchange`, `JsonBody`.

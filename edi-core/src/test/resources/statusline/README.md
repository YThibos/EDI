# Claude Code statusLine fixtures

Each file is one JSON document Claude Code passes on standard input to a `statusLine` command, as `StatusLinePayload` reads it.
The first two were recorded from Claude Code 2.1.302; their paths, ids and names are replaced, and every other value is as recorded.

| Fixture | Shape |
|---|---|
| `session-start.json` | recorded at session start: `current_usage` and `used_percentage` are `null`, both token totals are 0, and the usage limits are already there |
| `first-answer.json` | recorded after the first answer: `current_usage` with its cache fields, `prompt_cache`, and both usage limits with their resets |
| `without-limits-or-cache.json` | `first-answer.json` without `rate_limits`, `effort` and `prompt_cache`, its `current_usage` without cache fields and its context at 0.9 percent, as a session outside a subscription on a model without effort would send it |

# Claude Code transcript fixtures

Each file is the tail of a Claude Code session transcript, one JSON record per line, as `ClaudeTranscript` reads it.
The record shapes follow transcripts written by Claude Code 2.1.270 to 2.1.308; every identifier, path and text is synthetic.

| Fixture | Shape |
|---|---|
| `session-limit.jsonl` | the session ends on the usage-limit error record, with `quotaLimits.resetsAt`, followed by the metadata records Claude Code appends |
| `session-limit-error-params.jsonl` | the same stop where the reset is only in `apiErrorParams.rate_limit_info.resetsAt` |
| `weekly-limit.jsonl` | a weekly limit whose reset is days away |
| `spend-limit.jsonl` | the monthly spend limit, which names no reset |
| `working.jsonl` | the last conversational record is an ordinary answer |
| `overloaded.jsonl` | the last conversational record is another API error |
| `quoted-limit.jsonl` | a tool result quotes a limit message; the session did not stop on it |
| `limit-without-timestamp.jsonl` | a limit record that carries no timestamp |
| `truncated.jsonl` | the last line is cut off in the middle of a record |
| `context-full.jsonl` | the session ends on the API error a full context gives, `invalid_request` with "Prompt is too long", as Claude Code 2.1.308 writes it |
| `quoted-context-limit.jsonl` | an ordinary answer quotes "Prompt is too long"; the session did not stop on it |
| `missed-stop.jsonl` | the turn ends after its `Stop` hook failed because EDI's core was down: a `stop_hook_summary` with `hookErrors`, then `turn_duration`, as Claude Code 2.1.308 writes it |
| `error-stop.jsonl` | the turn ends on an `overloaded` API error after Claude Code's retries, while its `StopFailure` hook failed because EDI's core was down: the error record, then `turn_duration` |
| `answered-stop.jsonl` | the turn ends after a `Stop` hook EDI answered, with empty `hookErrors` |
| `blocked-stop.jsonl` | EDI blocked the stop, so the summary carries its reason and the turn goes on |
| `usage.jsonl` | assistant records repeat a message's usage once per content block |

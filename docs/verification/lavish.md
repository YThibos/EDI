# Lavish: poll results and the board listener

Verified on lavish-axi 0.1.88 (`lavish-axi --version`), whose results come from `@toon-format/toon` 2.3.1, on WSL2 Ubuntu 24.04, 17/10/2026.
EDI drives one command of Lavish, `lavish-axi poll`, and reads what it prints.

## Method

`lavish-axi --help`, `lavish-axi poll --help` and the encoder that `lavish-axi` ships were read for the command's flags and its output grammar.
Every poll result the old harness had captured from real research boards, 43 of them, was read with `LavishResult` in a throwaway test that was not kept.
The fixtures under `edi-core/src/test/resources/lavish/` were then written by that same encoder from invented content.
An end-to-end run used a scratch EDI home and a separate Lavish server (`LAVISH_AXI_PORT`, `LAVISH_AXI_STATE_DIR` and `LAVISH_AXI_NO_OPEN` set, so no other session was touched): a board was opened, armed through `POST /api/boards` with a worker's token and bound to an open decision, answered in a headless browser, and sent.

## Poll

| Question | Result on 0.1.88 |
|---|---|
| How a poll names its listener | `--owner <label>`, shown in session listings; EDI passes `edi:<task>#<board>`. |
| What happens to a second listener | It fails with `LISTENER_ACTIVE` unless it passes `--takeover`, which displaces the first one with `LISTENER_REPLACED`; EDI always passes `--takeover`, so a restarted core takes its boards back. |
| How long a poll waits | Until the captain sends feedback, ends the session, or leaves every review window disconnected past a grace period; `--timeout-ms` is for tests only, and EDI passes none. |
| How the output is written | TOON: two-space indentation, `key: value`, quoted strings with backslash escapes, tables as `name[n]{field,...}:` with one comma-separated row per entry, and other arrays as `name[n]:` with `- ` items. |

## Results seen

| Shape | What it looks like | `LavishResult` reads it as |
|---|---|---|
| Table form | `session:` with `status: feedback`, then `prompts[n]{uid,prompt,selector,tag,text}:` and one row per prompt | an answered round |
| List form | the same, but `prompts[n]:` with `- uid: "1"` items, whenever a prompt carries an `attachments[n]{id,type,path,mime,bytes,width,height,name}:` table | an answered round, attachments included |
| The captain's last words | `session_ended: true` and `ended_by: user`, with `prompts[1]{...}` | an answered round that ends the board |
| Empty close | `session_ended: true` without prompts | an empty close, which is not news |
| Disconnect | `status: browser_disconnected` and a `next_step`, without prompts | a disconnect; the listener asks again in the next round |
| Server error | `error:`, `code: SERVER_ERROR` and a `help[n]:` list | a failure, logged once |

Every captured result read as the round it was: 33 answered rounds with between one and ten prompts each, among them the list-form round of nine prompts and two attachments that the old harness had counted as none, 7 disconnects and 3 server errors.
A prompt queued through the board's script with `window.lavish.queuePrompt(text, { data })` arrives with the data appended to its words as `\n\nContext data:\n` and the data as indented JSON; `LavishResult` separates the two and keeps the data's top-level fields in their order.

## End to end

Arming the board started `lavish-axi poll <board> --owner edi:lantern-research#1 --takeover` as a child of the core.
The board's form queued `D59: option=A; note: once the pilot report is in` with the context data `{"decision": "D59", "option": "A", "note": "once the pilot report is in"}`, and Send to Agent delivered it.
Within a second D59 was answered through the `board` channel with option A and that verbatim text, and the scout's `edi inbox` held the round as message 1 with the prompt, its element and its context fields.

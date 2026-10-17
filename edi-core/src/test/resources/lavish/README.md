# Lavish poll result fixtures

Each file is what `lavish-axi poll` prints for one kind of result, written by the TOON encoder that lavish-axi 0.1.88 ships, `@toon-format/toon` 2.3.1, from invented content in the shape of results captured from real research boards.
`adapter.board.LavishResultTest` reads them; `docs/verification/lavish.md` says how the shapes were verified.

| File | Result |
|---|---|
| `list-form.toon` | an answered round in the list form, because one prompt carries two image attachments |
| `table-form.toon` | an answered round in the table form, one row per prompt |
| `ended-with-message.toon` | the captain's last words as the session ends |
| `ended-empty.toon` | a session the captain ended without feedback |
| `disconnected.toon` | the review window left disconnected past its grace period |
| `error.toon` | the Lavish server failing the poll |

# Memory and presence

## Memory

The captain's memory is the files `profile.md`, `machine.md` and `learnings.md` in `$EDI_HOME/memory/`, which the primary reads in its system prompt at every start (see [The Claude layer](claude-layer.md#the-primary-session)).
`edi memory check` holds that startup memory to its budget, set by the machine profile's `memory.startupBudgetTokens` ([ADR-120](decisions/ADR-120-startup-memory-is-measured-against-its-budget.md)).

```sh
edi memory check                                    # startup memory against its budget; exits 1 when it fails
edi memory stow-list                                # what /stow should curate
```

## Presence

Presence follows the captain: present, quiet or away ([ADR-030](decisions/ADR-030-presence-modes.md), [ADR-122](decisions/ADR-122-presence-is-held-by-the-core-and-followed-every-round.md)).
It turns quiet after a stretch without typing or when a meeting begins, gathers wakes while the chat is quiet, raises a Windows notification for what needs the captain, and briefs the captain on their return ([ADR-124](decisions/ADR-124-wakes-are-gathered-into-one-batch-while-the-chat-is-quiet.md), [ADR-125](decisions/ADR-125-notifications-for-the-captain-come-from-an-event-follower.md), [ADR-126](decisions/ADR-126-the-windows-side-runs-through-powershell.md), [ADR-127](decisions/ADR-127-the-captain-returns-to-a-brief-of-what-happened.md)).
The console shows the presence ([ADR-133](decisions/ADR-133-the-console-shows-the-presence.md)).

```sh
edi presence                                        # present, quiet or away
edi presence quiet
edi presence away --mandate - < words.txt           # the captain's /afk words, as typed
edi presence demo                                   # hold present for a demo; edi presence demo off ends it
```

The machine profile switches the Windows notifications and the Outlook calendar on, and the personal configuration sets how long a stretch without typing lasts, how long wakes are gathered and which calendar entries never mark a meeting (see [Configuration](configuration.md)).

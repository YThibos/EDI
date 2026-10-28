# Memory and presence

## Memory

The captain's memory is the files `profile.md`, `machine.md` and `learnings.md` in `$EDI_HOME/memory/`, which the primary reads in its system prompt at every start (see [The Claude layer](claude-layer.md#the-primary-session)).
`edi memory check` holds that startup memory to its budget, set by the machine profile's `memory.startupBudgetTokens` ([primary-startup-context](decisions/primary-startup-context.md)).
A learning is either a fact, dated with the day it was last confirmed, or a workaround that ends with the key of the ticket that removes it, such as "until EDI-69".
`edi memory stow-list` lists a workaround as expired once its ticket has landed and as unticketed when no task or ticket carries its key, and the session-start digest counts both.

```sh
edi memory check                                    # startup memory against its budget; exits 1 when it fails
edi memory stow-list                                # what /stow should curate
```

## Presence

Presence follows the captain: present, quiet or away ([presence](decisions/presence.md)).
It turns quiet after a stretch without typing or when a meeting begins, gathers wakes while the chat is quiet, raises a Windows notification for what needs the captain, and briefs the captain on their return ([wakes-and-turn-end](decisions/wakes-and-turn-end.md), [presence](decisions/presence.md), [windows-side](decisions/windows-side.md)).
The console shows the presence ([presence](decisions/presence.md)).
In the primary session the captain sets it with `/quiet`, `/quiet off`, `/present`, `/afk <words>`, `/demo` and `/demo off`, each a skill of its own name ([primary-anchor-and-skills](decisions/primary-anchor-and-skills.md)).

```sh
edi presence                                        # present, quiet or away
edi presence quiet
edi presence away --mandate - < words.txt           # the captain's /afk words, as typed
edi presence demo                                   # hold present for a demo; edi presence demo off ends it
```

The machine profile switches the Windows notifications and the Outlook calendar on, and the personal configuration sets how long a stretch without typing lasts, how long wakes are gathered and which calendar entries never mark a meeting (see [Configuration](configuration.md)).

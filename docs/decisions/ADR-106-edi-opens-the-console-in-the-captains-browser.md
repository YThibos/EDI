# ADR-106: EDI opens the console in the captain's browser through the Windows start command

**Date:** 17/10/2026

**Status:** Superseded by ADR-150

**Kind:** Design

**Context and constraints:** Section 15.2 (decision E16): `/sitrep board` prints the report, then the console's sitrep page address on one line, and EDI opens the page in the captain's Windows browser itself by running `cmd.exe /c start <url>` from WSL with a Windows working directory, so no link has to be found in the terminal.
Only adapters start programs, each program from the adapter that owns it (ADR-009).

**Decision:** `CaptainsBrowser`, in the new package `adapter.browser`, owns the program `CMD` and runs `cmd.exe /c start <sign-in link>` from `edi.console.windows-directory` (`/mnt/c` by default); `edi.console.browser` names another executable, which tests use.
`OpenConsole` issues a sign-in link (ADR-105) and asks the browser to open it.
When the browser opened, `edi console` and `edi sitrep --board` print only the console's address, `EDI console: http://127.0.0.1:<port>/ is open in your browser.`; when the Windows directory is missing or the start command fails, they print the sign-in link itself, so the captain can open it once within two minutes.
`edi sitrep --board` opens the console only after the report itself was printed.

**Motivation:** The command the specification names opens the default Windows browser from WSL without a helper, and printing the link only when nothing opened keeps a usable link out of transcripts in the common case.

**Alternatives considered:**
- Opening the browser from the command - the command starts no programs; every program runs in the core's adapters.
- `explorer.exe <url>` - it exits with a failure even when it opened the page, so success cannot be told from failure.

**Consequences:**
- Good: on both machines the captain sees the page appear without copying a link.
- Bad: a machine without Windows interop gets the printed link instead.

**Revisit when:** EDI runs on a machine without WSL.

**Sources:** EDI rewrite specification, section 15.2 (decision E16); ADR-009, ADR-105; `adapter.browser.CaptainsBrowser`, `adapter.process.Program.CMD`, `application.access.OpenConsole`, `web.console.ConsoleOpeningText`, `edi-cli` `ConsoleCommand`, `SitrepCommand`.

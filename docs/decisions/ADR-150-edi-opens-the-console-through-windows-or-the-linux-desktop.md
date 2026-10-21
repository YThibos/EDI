# ADR-150: EDI opens the console in the captain's browser through the Windows start command, or through xdg-open on a Linux desktop

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.2 (decision E16): EDI opens the console's sitrep page in the captain's browser itself, so no link has to be found in the terminal.
ADR-106 opened it only through `cmd.exe /c start <url>` from WSL, and named a machine without WSL as the moment to revisit it.
EDI now opens the sitrep page at every new primary session (ADR-151), which has to work on a plain Linux desktop as well as on WSL.
Only adapters start programs, each program from the adapter that owns it (ADR-009), and machine differences are found at runtime (ADR-006).

**Decision:** `CaptainsBrowser`, in `adapter.browser`, owns the programs `CMD` and `XDG_OPEN`.
When `edi.console.windows-directory` (`/mnt/c` by default) exists, it runs `cmd.exe /c start <sign-in link>` from that directory; otherwise, when `edi.console.desktop-display` names a display, it runs `xdg-open <sign-in link>` from the EDI home.
`edi.console.desktop-display` defaults to `WAYLAND_DISPLAY`, then `DISPLAY`, and `edi.console.browser` names another executable for either program, which tests use.
The opener gets 15 seconds to answer; one that fails or cannot run counts as not opened.
An opener still running after its patience counts as opened and is left running, never stopped.
`OpenConsole` issues a sign-in link (ADR-105) and asks the browser to open it.
When the browser opened, `edi console` and `edi sitrep --board` print only the console's address, `EDI console: http://127.0.0.1:<port>/ is open in your browser.`; when nothing opened, they print the sign-in link itself, so the captain can open it once within two minutes.
`edi sitrep --board` opens the console only after the report itself was printed.

**Motivation:** WSL keeps the Windows start command, which opens the captain's Windows browser even where WSLg also offers a Linux display, and `xdg-open` is the freedesktop.org way to open a link in the desktop's default browser.
Asking for a display before running `xdg-open` keeps a machine without a desktop, such as a build server, from falling back to a text browser that would wait for a terminal.
A short patience keeps a session start or a command from waiting on a browser that never answers.
`xdg-open` on a desktop without a desktop environment runs the browser in the foreground until it closes, so stopping a slow opener would close the very browser it just opened.

**Alternatives considered:**
- Choosing the opener in the machine profile - the presence of Windows and of a display is a fact of the machine EDI can see, and a setting could only disagree with it.
- Trying `xdg-open` first everywhere - on WSLg it would open a Linux browser beside the captain's Windows one.
- Opening the browser from the command - the command starts no programs; every program runs in the core's adapters.
- `explorer.exe <url>` - it exits with a failure even when it opened the page, so success cannot be told from failure.

**Consequences:**
- Good: the captain sees the page appear without copying a link on WSL and on a Linux desktop alike.
- Bad: a machine with neither Windows interop nor a display, or a desktop without `xdg-open`, gets the printed link instead.

**Revisit when:** EDI runs on macOS, or on a desktop where `xdg-open` does not reach the captain's browser.

**Sources:** EDI rewrite specification, section 15.2 (decision E16); ADR-006, ADR-009, ADR-105, ADR-106, ADR-151; `adapter.browser.CaptainsBrowser`, `adapter.process.Program.CMD`, `Program.XDG_OPEN`, `adapter.process.ExternalPrograms`, `application.access.OpenConsole`, `web.console.ConsoleOpeningText`, `edi-cli` `ConsoleCommand`, `SitrepCommand`.

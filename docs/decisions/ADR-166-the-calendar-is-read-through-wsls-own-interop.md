# ADR-166: Notifications and the calendar come from the Windows side through powershell.exe, and the calendar is read through WSL's own interop

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** ADR-126 runs notifications and the classic Outlook calendar through `powershell.exe` from WSL.
In daily use every calendar read failed for three days: `New-Object -ComObject Outlook.Application` answered 0x80080005 (CO_E_SERVER_EXEC_FAILURE) after about 35 seconds, while Outlook ran on the captain's desktop.
WSL starts a Windows program through the interop server named by `WSL_INTEROP`, and each terminal session has its own server, which starts programs with the rights of the terminal that opened the session.
The core inherits the server of the terminal it was started from, and when that terminal runs elevated, `powershell.exe` runs at the high integrity level.
COM does not hand a program at the high integrity level the Outlook already running at the medium level, and the second Outlook it then tries to start fails, so the read fails.
A systemd timer has no `WSL_INTEROP` and reaches WSL's own server, `/run/WSL/1_interop`, whose programs run at the medium level; the same export read Outlook from there while it failed from the terminal.
Without `WSL_INTEROP`, WSL finds the server of the nearest ancestor that has one, so leaving the variable out keeps the terminal's server.
Each program EDI runs belongs to one adapter (ADR-009), and the harness stays free of any machine's specifics (ADR-017).

**Decision:** This record supersedes ADR-126.
`Program.POWERSHELL` runs only from `adapter.windows`, with `-NoProfile -NonInteractive -EncodedCommand` and a bundled script, from the Windows-visible directory `/mnt/c`.
`WindowsToasts` shows a notification through the Windows toast API with the bundled `windows/toast.ps1`, only when the machine profile says `notify.windowsToast: true` and the Windows side is there; the title and the text travel base64-encoded and are XML-escaped inside PowerShell, so no text can change the script.
`OutlookCalendar` reads one day's entries with the bundled `windows/outlook-calendar.ps1`, only when the machine profile says `presence.calendar: outlook`, and starts it with `WSL_INTEROP=/run/WSL/1_interop`, so the read runs with the captain's ordinary rights whichever terminal started the core.
The script exports the classic Outlook calendar in the JSON shape Timesheet Mate's export prints (subject, start, end, all-day, response and busy status), and when it fails it prints the failure's own message as plain text and exits with 1, so the reason reads as one sentence; `OutlookCalendar` reads times with or without an offset.
How often the calendar is read, and how the captain learns that it cannot be, is ADR-163.
A failed notification returns false and changes nothing.

**Motivation:** WSL's own server gives the calendar read the rights of the desktop Outlook runs on, in the core and in a timer alike, without asking the captain to start the core from an unelevated terminal.

**Alternatives considered:**
- Leaving `WSL_INTEROP` out - WSL then walks up to the terminal's own server, and the read stays at the high level.
- `GetActiveObject` instead of `New-Object` - from the high level it answers 0x800401E3 (MK_E_UNAVAILABLE): the running Outlook is not visible there either.
- Starting the core from an unelevated terminal - a rule the captain has to remember, broken silently by the next elevated window.
- Running the toast through WSL's own server too - a toast talks to no program already running on the desktop, so it has no such reason to move.

**Consequences:**
- Good: the calendar reads in about four seconds from an elevated terminal, a plain one and a timer.
- Bad: the read depends on WSL keeping `/run/WSL/1_interop`; where it goes, the read fails, and ADR-163 tells the captain.

**Revisit when:** WSL changes how it names its interop servers, Timesheet Mate writes its calendar export to a file EDI can read, or EDI runs outside WSL.

**Sources:** EDI rewrite specification, section 12.7 (decision E24); ADR-009, ADR-017, ADR-122, ADR-126, ADR-163; [docs/verification/windows.md](../verification/windows.md); `adapter.process.Program`, `ProgramCall`, `adapter.windows.WindowsToasts`, `OutlookCalendar`, `WindowsScripts`, `windows/toast.ps1`, `windows/outlook-calendar.ps1`, `adapter.home.MachineProfile`, `WindowsSide`, `architecture.ArchitectureRulesTest`.

# ADR-126: Notifications and the calendar come from the Windows side of WSL through powershell.exe

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Section 12.7: in quiet the captain gets a Windows notification for each item that needs them, and an Outlook meeting in progress turns presence quiet where Timesheet Mate's calendar export is available, degrading to the typing timer otherwise.
Both machines run EDI inside WSL2, whose interop starts Windows programs such as `powershell.exe` from Linux; Timesheet Mate reads the classic Outlook calendar through COM from a PowerShell script that prints JSON.
Each program EDI runs belongs to one adapter (ADR-009), and the harness stays free of any machine's specifics (ADR-017).

**Decision:** `Program.POWERSHELL` runs only from `adapter.windows`, with `-NoProfile -NonInteractive -EncodedCommand` and a bundled script, from the Windows-visible directory `/mnt/c`.
`WindowsToasts` shows a notification through the Windows toast API with the bundled `windows/toast.ps1`, only when the machine profile says `notify.windowsToast: true` and the Windows side is there; the title and the text travel base64-encoded and are XML-escaped inside PowerShell, so no text can change the script.
`OutlookCalendar` reads one day's entries with the bundled `windows/outlook-calendar.ps1`, which exports the classic Outlook calendar in the JSON shape Timesheet Mate's export prints (subject, start, end, all-day, response and busy status), only when the machine profile says `presence.calendar: outlook`; it reads times with or without an offset.
`MeetingCalendar` reads the calendar at most every fifteen minutes, and when the export fails it logs why and presence follows the captain's typing alone.
A failed notification returns false and changes nothing.

**Motivation:** One adapter owns everything EDI does on the Windows side, and both uses are optional per machine, so the desktop without Outlook runs on the timer.

**Alternatives considered:**
- Running Timesheet Mate's own script from its checkout - would couple EDI to another repository's internals and its placeholders.
- The BurntToast PowerShell module - one more module to install on every machine.
- A notification from Linux - WSL has no desktop of its own to show it on.

**Consequences:**
- Good: the adapters are tested against a stand-in `powershell.exe` that records the script and answers a calendar export.
- Bad: each notification and each calendar read starts a PowerShell, which takes about a second.

**Revisit when:** Timesheet Mate writes its calendar export to a file EDI can read, or EDI runs outside WSL.

**Sources:** EDI rewrite specification, section 12.7 (decision E24); ADR-009, ADR-017, ADR-122; [docs/verification/windows.md](../verification/windows.md); `adapter.process.Program`, `adapter.windows.WindowsToasts`, `OutlookCalendar`, `WindowsScripts`, `windows/toast.ps1`, `windows/outlook-calendar.ps1`, `application.presence.MeetingCalendar`, `adapter.home.MachineProfile`, `WindowsSide`, `architecture.ArchitectureRulesTest`.

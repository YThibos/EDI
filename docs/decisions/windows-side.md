# Notifications and the calendar come from the Windows side through powershell.exe, the calendar read through WSL's own interop every two minutes

**Kind:** Architecture

## Context

In quiet the captain gets a Windows notification for each item that needs them, and an Outlook meeting in progress turns presence quiet ([presence](presence.md)).
Both machines run EDI inside WSL2, whose interop starts Windows programs such as `powershell.exe`; the time-booking tool already reads the classic Outlook calendar through COM from a PowerShell script.
WSL starts a Windows program through the interop server named by `WSL_INTEROP`, one per terminal session, with that terminal's rights; from an elevated terminal `powershell.exe` runs at the high integrity level, where COM does not hand over the Outlook already running at the medium level.
Each read starts a PowerShell that talks to Outlook for about four seconds.

## Decision

`Program.POWERSHELL` runs only from `adapter.windows`, with `-NoProfile -NonInteractive -EncodedCommand` and a bundled script, from `/mnt/c`.
`WindowsToasts` shows a notification with the bundled `windows/toast.ps1` when the machine profile says `notify.windowsToast: true`; the title and text travel base64-encoded and are XML-escaped inside PowerShell, so no text can change the script, and a failed notification changes nothing.
`OutlookCalendar` reads one day's entries with the bundled `windows/outlook-calendar.ps1` when the profile says `presence.calendar: outlook`, starting it with `WSL_INTEROP=/run/WSL/1_interop`, WSL's own server, so the read runs with the captain's ordinary rights whichever terminal started the core; the script prints the time-booking tool's JSON shape, or its failure's own message and exit code 1.
`MeetingCalendar` keeps the latest `CalendarReading` and reads again two minutes after it (`CalendarReading.READ_AGAIN_AFTER`) or on a new day; between readings every round finds a meeting in the entries it holds, and a failed read keeps them and the moment the calendar became unreadable (`UnreadableCalendar`).
`NoticeTheUnreadableCalendar` shows one notification for each spell of unreadability, in the first round the chat is quiet, and the sitrep and the turn report carry `Calendar: unreadable since ...` with the failure's words until it clears.

## Why

One adapter owns everything EDI does on the Windows side, and both uses are optional per machine, so a machine without Outlook runs on the typing timer.
WSL's own server gives the read the rights of the desktop Outlook runs on, without asking the captain to start the core from an unelevated terminal.
A read every two minutes notices a late meeting quickly at the cost of a short PowerShell, and a failure the captain sees once, and in every report until it clears, is acted on.

## Alternatives rejected

- Running the time-booking tool's own script from its checkout - couples EDI to another repository's internals.
- The BurntToast module, or a notification from Linux - one more module to install, or no desktop to show it on.
- Leaving `WSL_INTEROP` out, or `GetActiveObject` - the read stays at the high level and sees no running Outlook.
- Reading every round, or once a day - a PowerShell every 30 seconds, or meetings accepted during the day missed.
- A decision card or a notification per failed read - nothing to decide, or one notification every two minutes.

## Consequences

- Good: the adapters are tested against a stand-in `powershell.exe`, and the calendar reads in about four seconds from any terminal or a timer.
- Bad: each notification starts a PowerShell, the read depends on WSL keeping `/run/WSL/1_interop`, and a core restarted during a spell notifies once more.

## When to revisit

When WSL renames its interop servers, the calendar export becomes a file EDI can read, or EDI runs outside WSL.

## How we got here

- 17/10/2026: slice S8 ran notifications and the calendar through `powershell.exe`, reading the calendar at most every fifteen minutes.
- 22/10/2026: every read had failed for three days from an elevated terminal with only a log warning, so the read moved to WSL's own interop server, every two minutes, and an unreadable calendar is told to the captain once (EDI-34).

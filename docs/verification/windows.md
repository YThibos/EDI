# The Windows side: notifications and the Outlook calendar

Verified on WSL2 Ubuntu 24.04 under Windows 11, with Windows PowerShell 5.1 reached as `powershell.exe` through WSL interop, 17/10/2026.
The scripts are the core's own resources, `windows/toast.ps1` and `windows/outlook-calendar.ps1`, run the way `WindowsToasts` and `OutlookCalendar` run them (ADR-126, ADR-166).

## Method

Each script was read from `edi-core/src/main/resources/windows/`, filled in, encoded as UTF-16LE base64 and run with `powershell.exe -NoProfile -NonInteractive -EncodedCommand <script>` from `/mnt/c`.
The calendar's output was reduced to counts and the shape of its fields before it was kept, so no meeting subject left the machine.

## Notifications

- The toast script exited with 0 and showed one notification titled "EDI verification", whose text held `<`, `>`, `&` and double quotes, so the escaping holds.
- PowerShell wrote a CLIXML progress record ("Preparing modules for first use") on standard error on its first run; it is not an error, and EDI judges the run by its exit code alone.

## The Outlook calendar

- The export of a day without meetings printed an empty JSON array and exited with 0.
- The export of a working day printed one object per item with `Subject`, `Start`, `End`, `AllDay`, `ResponseStatus` and `BusyStatus`.
- `Start` and `End` are UTC in the round-trip format with seven fractional digits and a `Z`, such as `2026-10-19T07:00:00.0000000Z`, which `OffsetDateTime.parse` reads.
- An accepted meeting showed `ResponseStatus` 3 and `BusyStatus` 2, the numbering `CalendarEntry.Response` and `CalendarEntry.Showing` follow.
- Classic Outlook must be installed for the COM export; where it is not, the export fails, and EDI follows the captain's typing alone.

## The calendar from an elevated terminal

Verified 22/10/2026 on the same set-up, with classic Outlook running on the desktop at the medium integrity level.

- Run through the interop server of a terminal running elevated, `whoami /groups` reported the high mandatory level, and the export failed after about 35 seconds with 0x80080005 (CO_E_SERVER_EXEC_FAILURE).
- `[Runtime.InteropServices.Marshal]::GetActiveObject('Outlook.Application')` from the same server failed with 0x800401E3 (MK_E_UNAVAILABLE).
- Without `WSL_INTEROP`, the program still ran at the high level, through the server of the terminal the shell descends from.
- With `WSL_INTEROP=/run/WSL/1_interop`, the program ran at the medium level and the export listed the day's meetings in about four seconds.
- A systemd user timer reaches the same server, since it has no `WSL_INTEROP` and descends from the first process, which is why a timer's export worked while the core's failed.
- On failure the script prints the exception's message as plain text on standard error and exits with 1, with no CLIXML around it.

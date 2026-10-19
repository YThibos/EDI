# Backlog

## In flight
- [ ] lantern-rework - SHIP: rework the lantern controller (repo: lighthouse) (kind: ship) (since 2026-09-01)
  Spec: the keeper's notes, section 2.
- [ ] tide-tables - SCOUT: read the tide tables (for the harbour) and report (repo: harbour) (since 2026-09-20) (hold: fm-hold-v1:Q2FwdGFpbiAoMjAyNi0xMC0wMSk6IHdhaXQgZm9yIHRoZSBzdXJ2ZXkuIFJlc3VtZSBvbmx5IG9uIGhpcyB3b3JkLg==) (hold-kind: captain)
  Captain hold set: 2026-10-01T08:00:00Z

  Earlier notes about the tides.
- **beacon-colour** - Pick the beacon colour (repo: lighthouse) (kind: captain) (since 2026-10-10) (hold: fm-hold-v1:V2hpY2ggYmVhY29uIGNvbG91cj8=) (hold-kind: captain)
  Captain hold set: 2026-10-10T10:00:00Z

  Captain decision id: D57
  Captain decision card: {"title":"Which beacon colour?","context":"Red is traditional; green is seen further.","projects":["lighthouse"],"options":[{"key":"A","text":"Red"},{"key":"B","text":"Green"}],"recommend":{"key":"A","why":"the charts say red"}}
## Captain's Call (manual holds, all resolved)
- [x] old-call-one - RESOLVED 2026-07-01: kept the old lamp.

- [x] old-call-two - RESOLVED 2026-07-02: dropped the second mast.

## Queued
- [ ] harbour-wall - CHORE: patch the harbour wall (repo: harbour) (since 2026-08-01)
- [ ] keeper-docs - Write the keeper's handbook (repo: lighthouse) (kind: docs) (since 2026-08-02)
- [ ] fog-horn - Tune the fog horn blocked-by: lantern-rework (repo: lighthouse) (kind: ship) (since 2026-08-03)
- [ ] gull-survey - Count the gulls (repo: harbour) (kind: task) (since 2026-08-04) blocked-by: bird-census - waits on the census
- [ ] loose-end - Tidy the stores (repo: -) (kind: task) (since 2026-08-05)
- [ ] visit-day - Open day for visitors (repo: lighthouse) (kind: captain) (since 2026-08-06) (hold: Revisit after the season) (hold-kind: captain) (hold-until: 2026-11-01)
- [ ] relay-mast - PERSISTENT SECONDMATE: keeps the relay mast (repo: harbour) (since 2026-08-07)
- [ ] ferry-timetable - EDI-7 Publish the ferry timetable (repo: harbour) (kind: ship) (since 2026-08-08)
  Its follow-up branch is feature/EDI-9, named only here.
- [ ] boat-paint - Paint the boats (repo: harbour) (kind: ship) (since 2026-08-09) (hold: waiting on supplies) (hold-kind: external)
- [ ] fog-horn - A second entry under a used id (repo: lighthouse) (kind: ship) (since 2026-08-10)
- [ ] Gate-Repair - Repair the gate (repo: harbour) (kind: ship) (since 2026-08-11)
- [ ] night-watch - Wait for a stormy night (repo: harbour) (kind: scout) (since 2026-08-12) (hold: waits for a stormy night to test the lamp) (hold-until: 2026-11-15)
- [ ] old-harness-polish - Polish the old harness's own scripts blocked-by: lantern-rework (repo: firstofficer) (kind: ship) (since 2026-08-13)
- [ ] polish-follow-up - Follow the old harness polish blocked-by: old-harness-polish (repo: lighthouse) (kind: ship) (since 2026-08-14)
This line is not a task entry.
## Done
- [x] flag-pole - Replace the flag pole (repo: lighthouse) (kind: ship) (done 2026-09-30)
- [x] lamp-choice - Choose the lamp (repo: lighthouse) (kind: captain) (done 2026-10-05) (hold: fm-hold-v1:V2hpY2ggbGFtcD8=) (hold-kind: captain)
  Resolution recorded by fm-captain-hold.
  Resolution mode: answered

  Captain decision:
  Captain (2026-10-05): "D55, A"
  Answer: A - the LED lamp.

  Captain decision id: D55
  Captain decision card: {"title":"Which lamp?","context":"","projects":["lighthouse"],"options":[{"key":"A","text":"LED"},{"key":"B","text":"Oil"}],"recommend":{"key":"A","why":"it lasts longer"}}

- [x] paint-choice - Choose the paint (repo: harbour) (kind: captain) (done 2026-10-06)
  Captain decision:
  Captain answered this call through the captured result board-1 sequence 2.
  Answer: later
  Answer as shown to the captain: Which paint? -> later - after the storm season

  Captain decision id: D56
  Captain decision card: {"title":"Which paint?","context":"Two coats or one.","projects":["harbour"],"options":[{"key":"A","text":"Two coats"},{"key":"B","text":"One coat"}],"recommend":{"key":"A","why":"salt air"}}
- [x] silent-choice - A settled call without its answer (repo: harbour) (kind: captain) (done 2026-10-07)
  Captain decision id: D54
  Captain decision card: {"title":"Which rope?","context":"","projects":["harbour"],"options":[{"key":"A","text":"Hemp"},{"key":"B","text":"Nylon"}],"recommend":{"key":"B","why":"it does not rot"}}

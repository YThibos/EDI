---
name: demo
description: >-
  Owns the captain's /demo and /demo off.
  Load it when the captain types /demo or /demo off, or says in their own words that a demo starts or ends.
user-invocable: true
---

# demo

A demo holds present through automatic and calendar quiet until `/demo off`, the end of the current meeting or three hours.

## Plain `/demo`

1. Run `edi presence demo`.
2. Run `edi personality` and speak with the demo personality it prints until the demo ends.
3. End the turn as a present turn ends, naming in its plain line until when the demo holds.

## `/demo off`

1. Run `edi presence demo off`.
2. Run `edi personality` and speak with the captain's own personality it prints again.
3. End the turn as a present turn ends, naming the new presence in its plain line.

## The argument

- Only the literal word `off` ends the demo.
- Any other argument is named as unknown in one line, nothing is set, and the line names the presence `edi presence` shows, since the typed message itself ends quiet or away.

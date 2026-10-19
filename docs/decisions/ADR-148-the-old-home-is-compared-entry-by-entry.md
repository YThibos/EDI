# ADR-148: AT-41 and AT-47 compare every entry of the old home but its access times, and name each one that changed

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain asked for an import that never modifies the old home, and ADR-136 has the old home only read.
ADR-140 rejects hard links into the archive because making the archive read-only would change the old home's own files.
AT-41 and AT-47 compared the bytes of the old home's regular files before and after, following links, so a changed permission, a file rewritten with the same bytes or an added empty folder went unseen.
A failure printed both maps of paths to byte arrays, more than 200 KB, and the log view cut it off before the entry that differed.

**Decision:** `BeyondTheInvariantsTest` describes each entry of the old home, without following links, by its permissions, its time of last modification and what it holds: a folder, a file's SHA-256 or a link's target.
After the dry run or the import it asserts that no description changed, and lists each entry that did as `<entry>: <before> -> <after>`, with `absent` for an entry added or removed.
Access times are left out, because reading is what the import does and a read may update them.

**Motivation:** The comparison covers what "never modifies" promises and nothing a read changes, and a failure names the entry and how it changed in a few lines.

**Alternatives considered:**
- Leaving the clones' `.git` folders out - the clones are part of the old home, and an import that fetched or changed a remote would go unseen.
- Comparing the time of last status change as well - Java reads it only through the platform-specific `unix:ctime` attribute.
- AssertJ's map comparison of the descriptions - its message prints both maps whole before the entries that differ.

**Consequences:**
- Good: a chmod, a rewrite or a new entry in the old home fails AT-41 or AT-47 with the entry's name.
- Bad: the comparison holds only while nothing else writes to the old home during the test, which ADR-147 secures for its clones.

**Revisit when:** The import is allowed to write to the old home, or the old home is archived (section 20.4).

**Sources:** ADR-136, ADR-140, ADR-147; main's CI run after slice S10; `edi-core` `invariant.BeyondTheInvariantsTest`; AT-41, AT-47.

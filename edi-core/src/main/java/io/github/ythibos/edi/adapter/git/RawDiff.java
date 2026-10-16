package io.github.ythibos.edi.adapter.git;

import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.delivery.ChangedPath;
import io.github.ythibos.edi.domain.delivery.PathChange;

final class RawDiff {

    private static final String NUL = "\0";
    private static final char RENAMED = 'R';
    private static final char COPIED = 'C';
    private static final char ADDED = 'A';
    private static final char DELETED = 'D';
    private static final char TYPE_CHANGED = 'T';
    private static final int OLD_MODE = 0;
    private static final int NEW_MODE = 1;
    private static final int STATUS = 4;

    private final List<String> fields;
    private final List<ChangedPath> changes = new ArrayList<>();
    private int next;

    private RawDiff(String raw) {
        this.fields = List.of(raw.split(NUL));
    }

    static List<ChangedPath> changedPaths(String raw) {
        RawDiff diff = new RawDiff(raw);
        while (diff.next + 1 < diff.fields.size()) {
            diff.readOneChange();
        }
        return List.copyOf(diff.changes);
    }

    private void readOneChange() {
        String[] meta = fields.get(next++).substring(1).split(" ");
        char status = meta[STATUS].charAt(0);
        if (status == RENAMED) {
            String former = fields.get(next++);
            changes.add(ChangedPath.renamed(former, fields.get(next++)));
        } else if (status == COPIED) {
            next++;
            changes.add(ChangedPath.of(fields.get(next++), PathChange.ADDED));
        } else {
            changes.add(ChangedPath.of(fields.get(next++), changeOf(status, meta)));
        }
    }

    private static PathChange changeOf(char status, String[] meta) {
        if (status == ADDED) {
            return PathChange.ADDED;
        }
        if (status == DELETED) {
            return PathChange.DELETED;
        }
        if (status == TYPE_CHANGED || !meta[OLD_MODE].equals(meta[NEW_MODE])) {
            return PathChange.MODE_CHANGED;
        }
        return PathChange.MODIFIED;
    }
}

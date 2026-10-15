package io.github.ythibos.edi.adapter.git;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.publish.AddedLine;

final class UnifiedDiff {

    private static final String FILE_HEADER = "diff --git ";
    private static final String NEW_PATH = "+++ ";
    private static final String NEW_PATH_PREFIX = "b/";
    private static final String NO_FILE = "/dev/null";
    private static final String PATH_WITH_SPACES_TERMINATOR = "\t";
    private static final String ADDITION = "+";
    private static final String CONTEXT = " ";
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    private final List<AddedLine> added = new ArrayList<>();
    private String path;
    private boolean insideHunk;
    private int nextLine;

    private UnifiedDiff() {
    }

    static List<AddedLine> addedLines(String patch) {
        UnifiedDiff diff = new UnifiedDiff();
        patch.lines().forEach(diff::read);
        return List.copyOf(diff.added);
    }

    private void read(String line) {
        if (line.startsWith(FILE_HEADER)) {
            startFile();
        } else if (!insideHunk && line.startsWith(NEW_PATH)) {
            path = newPath(line.substring(NEW_PATH.length()));
        } else if (line.startsWith("@@")) {
            startHunk(line);
        } else if (insideHunk) {
            readHunkLine(line);
        }
    }

    private void startFile() {
        path = null;
        insideHunk = false;
    }

    private static String newPath(String header) {
        String path = header.endsWith(PATH_WITH_SPACES_TERMINATOR) ? header.substring(0, header.length() - 1) : header;
        return path.equals(NO_FILE) ? null : path.substring(NEW_PATH_PREFIX.length());
    }

    private void startHunk(String header) {
        Matcher hunk = HUNK_HEADER.matcher(header);
        insideHunk = hunk.find();
        nextLine = insideHunk ? Integer.parseInt(hunk.group(1)) : 0;
    }

    private void readHunkLine(String line) {
        if (line.startsWith(ADDITION)) {
            recordAddition(line.substring(ADDITION.length()));
        } else if (line.startsWith(CONTEXT)) {
            nextLine++;
        }
    }

    private void recordAddition(String text) {
        if (path != null) {
            added.add(new AddedLine(path, nextLine, text));
        }
        nextLine++;
    }
}

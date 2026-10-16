package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.publish.AddedLine;

public final class SnapshotVersions {

    private static final String BUILD_FILE = "pom.xml";
    private static final Pattern SNAPSHOT = Pattern.compile("-SNAPSHOT\\b", Pattern.CASE_INSENSITIVE);

    private SnapshotVersions() {
    }

    public static List<AddedLine> addedIn(List<AddedLine> addedLines) {
        return addedLines.stream().filter(SnapshotVersions::isInABuildFile).filter(line -> SNAPSHOT.matcher(line.text()).find()).toList();
    }

    public static String refusalOf(List<AddedLine> snapshots) {
        return "push-bound work carries no -SNAPSHOT version, and the branch adds one at "
                + String.join(", ", snapshots.stream().map(line -> line.path() + ":" + line.number()).toList());
    }

    private static boolean isInABuildFile(AddedLine line) {
        return line.path().equals(BUILD_FILE) || line.path().endsWith("/" + BUILD_FILE);
    }
}

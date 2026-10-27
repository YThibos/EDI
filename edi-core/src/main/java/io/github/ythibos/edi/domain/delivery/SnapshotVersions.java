package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OutgoingCommit;
import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.domain.publish.PublishRule;

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

    public static List<PublishFinding> findIn(OutgoingChanges changes) {
        return changes.commits().stream().flatMap(SnapshotVersions::findIn).toList();
    }

    private static boolean isInABuildFile(AddedLine line) {
        return line.path().equals(BUILD_FILE) || line.path().endsWith("/" + BUILD_FILE);
    }

    private static Stream<PublishFinding> findIn(OutgoingCommit commit) {
        return addedIn(commit.addedLines()).stream().map(snapshot -> findingOn(snapshot, commit));
    }

    private static PublishFinding findingOn(AddedLine snapshot, OutgoingCommit commit) {
        return PublishFinding.onLine(PublishRule.SNAPSHOT_VERSION, snapshot.path(), snapshot.number(), commit.id(), "a -SNAPSHOT version");
    }
}

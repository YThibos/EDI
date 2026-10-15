package io.github.ythibos.edi.domain.publish;

public record PublishFinding(PublishRule rule, String commitId, FindingPlace place, String reason) {

    private static final int SHORT_COMMIT_ID_LENGTH = 7;

    public static PublishFinding onLine(PublishRule rule, String path, int line, String commitId, String reason) {
        return new PublishFinding(rule, commitId, new FindingPlace.Line(path, line), reason);
    }

    public static PublishFinding onPath(PublishRule rule, String path, String commitId, String reason) {
        return new PublishFinding(rule, commitId, new FindingPlace.File(path), reason);
    }

    public static PublishFinding onCommit(PublishRule rule, String part, String commitId, String reason) {
        return new PublishFinding(rule, commitId, new FindingPlace.CommitPart(part), reason);
    }

    public String location() {
        return place.describedIn(commitId.substring(0, Math.min(SHORT_COMMIT_ID_LENGTH, commitId.length())));
    }

    @Override
    public String toString() {
        return location() + ": " + reason;
    }

    PublishFinding hidingPathsDeniedBy(DenyList denyList, OutgoingChanges changes) {
        if (place instanceof FindingPlace.NamedPath named && denyList.appearsIn(named.path())) {
            return new PublishFinding(rule, commitId, named.hiddenAs(changes.positionOf(commitId, named.path())), reason);
        }
        return this;
    }
}

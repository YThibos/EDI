package io.github.ythibos.edi.domain.publish;

public record PublishFinding(PublishRule rule, String location, String reason) {

    private static final int SHORT_COMMIT_ID_LENGTH = 7;

    public static PublishFinding onLine(PublishRule rule, String path, int line, String commitId, String reason) {
        return new PublishFinding(rule, path + ":" + line + " in commit " + shortened(commitId), reason);
    }

    public static PublishFinding onPath(PublishRule rule, String path, String commitId, String reason) {
        return new PublishFinding(rule, path + " in commit " + shortened(commitId), reason);
    }

    public static PublishFinding onCommit(PublishRule rule, String part, String commitId, String reason) {
        return new PublishFinding(rule, part + " of commit " + shortened(commitId), reason);
    }

    @Override
    public String toString() {
        return location + ": " + reason;
    }

    private static String shortened(String commitId) {
        return commitId.substring(0, Math.min(SHORT_COMMIT_ID_LENGTH, commitId.length()));
    }
}

package io.github.ythibos.edi.domain.tracker;

public enum DriftKind {
    MERGED_BEFORE_ITS_MERGED_STATE("work merged, but the ticket is before its merged state"),
    DONE_BEFORE_PRODUCTION("a story or bug in Done before its work reached production"),
    IN_REVIEW_WITHOUT_AN_OPEN_REQUEST("in a review state with no open request"),
    IMPLEMENTED_WITHOUT_A_COMMIT("in an implemented state with no commit on the base branch");

    private final String sentence;

    DriftKind(String sentence) {
        this.sentence = sentence;
    }

    public String sentence() {
        return sentence;
    }
}

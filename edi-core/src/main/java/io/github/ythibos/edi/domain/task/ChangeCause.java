package io.github.ythibos.edi.domain.task;

public enum ChangeCause {
    STEER_SENT("a steer was sent"),
    PIPELINE_RUN_STARTED("a pipeline run started"),
    WORKER_WORKING("its worker reported working"),
    REQUEST_HEAD_MOVED("its request's head moved"),
    BASE_MOVED("its base moved"),
    REQUEST_CLOSED("its request was closed");

    private final String sentence;

    ChangeCause(String sentence) {
        this.sentence = sentence;
    }

    public String sentence() {
        return sentence;
    }
}

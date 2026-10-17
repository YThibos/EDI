package io.github.ythibos.edi.domain.authority;

public enum CaptainWordAct {
    PROMOTE("promote a scout to a ship"),
    DROP("drop a task"),
    DISCARD_WORK("discard work that has not landed"),
    TOUCH_A_SECRET("touch a secret"),
    STOP_RUNNING_WORKERS("stop the workers that are running");

    private final String sentence;

    CaptainWordAct(String sentence) {
        this.sentence = sentence;
    }

    public String sentence() {
        return sentence;
    }
}

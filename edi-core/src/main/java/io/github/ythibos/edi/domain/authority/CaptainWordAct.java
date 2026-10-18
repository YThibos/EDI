package io.github.ythibos.edi.domain.authority;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum CaptainWordAct {
    PROMOTE("promote a scout to a ship", true),
    DROP("drop a task", true),
    DISCARD_WORK("discard work that has not landed", true),
    TOUCH_A_SECRET("touch a secret", false),
    STOP_RUNNING_WORKERS("stop the workers that are running", false);

    private final String sentence;
    private final boolean onATask;

    CaptainWordAct(String sentence, boolean onATask) {
        this.sentence = sentence;
        this.onATask = onATask;
    }

    public static CaptainWordAct onATaskNamed(String word) {
        return Arrays.stream(values())
                .filter(act -> act.onATask && act.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A decision option authorises " + wordsOfTheActsOnATask() + ", not " + word + "."));
    }

    public String sentence() {
        return sentence;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String wordsOfTheActsOnATask() {
        return Arrays.stream(values()).filter(act -> act.onATask).map(CaptainWordAct::word).collect(Collectors.joining(", "));
    }
}

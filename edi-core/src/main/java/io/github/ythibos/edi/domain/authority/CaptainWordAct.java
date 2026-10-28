package io.github.ythibos.edi.domain.authority;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum CaptainWordAct {
    PROMOTE("promote a scout to a ship", true),
    DROP("drop a task", true),
    DISCARD_WORK("discard work that has not landed", true),
    EFFORT_ABOVE_HIGH("run one task above high, the effort ceiling", false),
    TOUCH_A_SECRET("touch a secret", false),
    STOP_RUNNING_WORKERS("stop the workers that are running", false);

    private final String sentence;
    private final boolean byAnOption;

    CaptainWordAct(String sentence, boolean byAnOption) {
        this.sentence = sentence;
        this.byAnOption = byAnOption;
    }

    public static CaptainWordAct authorisableByAnOption(String word) {
        return Arrays.stream(values())
                .filter(act -> act.byAnOption && act.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A decision option authorises " + wordsOfTheActsAnOptionAuthorises() + ", not "
                        + word + "."));
    }

    public static CaptainWordAct recordableForAWord(String word) {
        if (!EFFORT_ABOVE_HIGH.word().equals(word)) {
            throw new IllegalArgumentException("The captain's word is recorded for " + EFFORT_ABOVE_HIGH.word() + " alone, not " + word + ".");
        }
        return EFFORT_ABOVE_HIGH;
    }

    public String sentence() {
        return sentence;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String wordsOfTheActsAnOptionAuthorises() {
        return Arrays.stream(values()).filter(act -> act.byAnOption).map(CaptainWordAct::word).collect(Collectors.joining(", "));
    }
}

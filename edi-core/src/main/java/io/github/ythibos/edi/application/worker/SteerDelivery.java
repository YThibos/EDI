package io.github.ythibos.edi.application.worker;

import java.util.Locale;

public enum SteerDelivery {
    TYPED_INTO_THE_IDLE_PROMPT,
    AT_THE_NEXT_STOP,
    WAITING_FOR_THE_PANE,
    WAITING_FOR_A_LIVE_WORKER;

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

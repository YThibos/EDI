package io.github.ythibos.edi.adapter.fork;

import java.util.List;

public record ForkDecision(Integer number, ForkDecisionCard card, String unreadableCard, List<String> recordedAnswer) {

    public ForkDecision {
        recordedAnswer = List.copyOf(recordedAnswer);
    }
}

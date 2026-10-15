package io.github.ythibos.edi.application.decision;

import java.time.Instant;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.OptionKey;

public record GivenAnswer(String verbatim, OptionKey option, String interpretation, AnswerChannel channel) {

    CaptainAnswer recordedAt(Instant at) {
        return new CaptainAnswer(verbatim, option, interpretation, at, channel);
    }
}

package io.github.ythibos.edi.web.decision;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.orEmpty;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;

record AnswerRequest(String option, String text, String interpretation) {

    GivenAnswer givenInChat() {
        if (text == null) {
            throw new IllegalArgumentException("An answer records the captain's own words.");
        }
        return new GivenAnswer(text, optional(option, OptionKey::parse), orEmpty(interpretation), AnswerChannel.CHAT);
    }
}

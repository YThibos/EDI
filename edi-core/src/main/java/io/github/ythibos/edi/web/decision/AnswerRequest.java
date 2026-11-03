package io.github.ythibos.edi.web.decision;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.orEmpty;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;

record AnswerRequest(String option, Integer message, Integer line, String text, String interpretation) {

    GivenAnswer givenThrough(AnswerChannel channel, String verbatim) {
        return new GivenAnswer(verbatim, optional(option, OptionKey::parse), orEmpty(interpretation), channel);
    }

    Citation citation() {
        return Citation.of(message, line, text);
    }

    String verbatim() {
        if (text == null) {
            throw new IllegalArgumentException("An answer records the captain's own words.");
        }
        return text;
    }
}

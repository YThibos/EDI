package io.github.ythibos.edi.web.authority;

import static io.github.ythibos.edi.web.RequestValues.optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.decision.TaskReference;

record SaidRequest(Integer message, Integer line, String text, String task, @JsonProperty("for") String forAct) {

    Citation citation() {
        return Citation.of(message, line, text);
    }

    TaskReference concernedTask() {
        return optional(task, TaskReference::parse);
    }

    CaptainWordAct act() {
        return optional(forAct, CaptainWordAct::recordableForAWord);
    }
}

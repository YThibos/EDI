package io.github.ythibos.edi.web.personality;

import java.util.List;
import java.util.function.Function;

import io.github.ythibos.edi.domain.personality.PersonalityChange;
import io.github.ythibos.edi.web.RequestValues;

record PersonalityRequest(List<String> change) {

    PersonalityChange chosenChange() {
        return PersonalityChange.parse(RequestValues.each(change, Function.identity()));
    }
}

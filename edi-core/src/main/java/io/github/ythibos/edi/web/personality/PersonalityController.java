package io.github.ythibos.edi.web.personality;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.personality.ChangePersonality;
import io.github.ythibos.edi.application.personality.PersonalityInForce;
import io.github.ythibos.edi.application.personality.PersonalityQuery;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class PersonalityController {

    private final PersonalityQuery personality;
    private final ChangePersonality change;

    PersonalityController(PersonalityQuery personality, ChangePersonality change) {
        this.personality = personality;
        this.change = change;
    }

    @GetMapping("/api/personality")
    PersonalityInForce current() {
        return personality.current();
    }

    @PostMapping("/api/personality")
    PersonalityInForce change(@RequestBody PersonalityRequest request, Caller caller) {
        caller.requirePrimary("edi personality");
        return change.change(request.chosenChange());
    }
}

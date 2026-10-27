package io.github.ythibos.edi.web.personality;

import org.springframework.http.MediaType;
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

    private static final String PERSONALITY = "/api/personality";

    private final PersonalityQuery personality;
    private final ChangePersonality change;

    PersonalityController(PersonalityQuery personality, ChangePersonality change) {
        this.personality = personality;
        this.change = change;
    }

    @GetMapping(path = PERSONALITY, produces = MediaType.TEXT_PLAIN_VALUE)
    PersonalityInForce current() {
        return personality.current();
    }

    @GetMapping(path = PERSONALITY, produces = MediaType.APPLICATION_JSON_VALUE)
    PersonalityView currentView() {
        return PersonalityView.of(current());
    }

    @PostMapping(path = PERSONALITY, produces = MediaType.TEXT_PLAIN_VALUE)
    PersonalityInForce change(@RequestBody PersonalityRequest request, Caller caller) {
        requireAllowed(caller);
        return change.change(request.chosenChange());
    }

    @PostMapping(path = PERSONALITY, produces = MediaType.APPLICATION_JSON_VALUE)
    PersonalityView changeView(@RequestBody PersonalityRequest request, Caller caller) {
        return PersonalityView.of(change(request, caller));
    }

    private static void requireAllowed(Caller caller) {
        if (!(caller instanceof Caller.Captain)) {
            caller.requirePrimary("edi personality");
        }
    }
}

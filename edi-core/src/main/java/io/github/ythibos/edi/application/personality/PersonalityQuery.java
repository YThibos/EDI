package io.github.ythibos.edi.application.personality;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.domain.personality.CaptainsPersonality;

@Service
public class PersonalityQuery {

    private final PersonalConfiguration personal;
    private final PresenceQuery presence;
    private final Clock clock;

    public PersonalityQuery(PersonalConfiguration personal, PresenceQuery presence, Clock clock) {
        this.personal = personal;
        this.presence = presence;
        this.clock = clock;
    }

    public PersonalityInForce current() {
        return inForce(personal.personality());
    }

    PersonalityInForce inForce(CaptainsPersonality captains) {
        return new PersonalityInForce(personal.address(), captains, presence.current().demoHoldsAt(clock.now()));
    }
}

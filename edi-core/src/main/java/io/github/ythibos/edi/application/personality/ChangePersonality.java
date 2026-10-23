package io.github.ythibos.edi.application.personality;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.personality.PersonalityChange;

@Service
public class ChangePersonality {

    private final PersonalConfiguration personal;
    private final PersonalityQuery personality;

    public ChangePersonality(PersonalConfiguration personal, PersonalityQuery personality) {
        this.personal = personal;
        this.personality = personality;
    }

    public PersonalityInForce change(PersonalityChange change) {
        CaptainsPersonality changed = personal.personality().changedBy(change);
        personal.choose(changed);
        return personality.inForce(changed);
    }
}

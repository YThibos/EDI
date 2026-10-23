package io.github.ythibos.edi.application.personality;

import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.status.CaptainAddress;

public record PersonalityInForce(CaptainAddress address, CaptainsPersonality captains, boolean demoHolds) {

    public Personality speaking() {
        return captains.speaking(demoHolds);
    }
}

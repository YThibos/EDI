package io.github.ythibos.edi.web.presence;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PresenceText implements TextPresenter<Presence> {

    @Override
    public Class<Presence> presents() {
        return Presence.class;
    }

    @Override
    public String text(Presence presence) {
        return "Presence: " + PresenceWords.of(presence) + ".\n";
    }
}

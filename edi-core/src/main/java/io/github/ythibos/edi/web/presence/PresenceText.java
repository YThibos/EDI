package io.github.ythibos.edi.web.presence;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PresenceText implements TextPresenter<PresenceView> {

    @Override
    public Class<PresenceView> presents() {
        return PresenceView.class;
    }

    @Override
    public String text(PresenceView presence) {
        return "Presence: " + presence.words() + ".\n";
    }
}

package io.github.ythibos.edi.web.presence;

import static io.github.ythibos.edi.web.RequestValues.required;

record PresenceRequest(String mode, String mandate) {

    static final String DEMO_OFF = "demo-off";

    String chosenMode() {
        return required(mode, "mode: present, quiet, away, demo or demo-off");
    }

    String mandateWords() {
        return required(mandate, "mandate, the captain's own words for his time away");
    }
}

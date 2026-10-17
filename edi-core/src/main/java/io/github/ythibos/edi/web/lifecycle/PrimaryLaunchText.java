package io.github.ythibos.edi.web.lifecycle;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.primary.PrimaryLaunch;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PrimaryLaunchText implements TextPresenter<PrimaryLaunch> {

    @Override
    public Class<PrimaryLaunch> presents() {
        return PrimaryLaunch.class;
    }

    @Override
    public String text(PrimaryLaunch launch) {
        return "session: " + launch.sessionName() + "\n"
                + "directory: " + launch.directory() + "\n"
                + "settings: " + launch.settings() + "\n"
                + "system prompt: " + launch.systemPrompt() + "\n";
    }
}

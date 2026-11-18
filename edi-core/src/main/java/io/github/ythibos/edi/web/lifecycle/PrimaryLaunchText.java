package io.github.ythibos.edi.web.lifecycle;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.primary.PrimaryLaunch;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PrimaryLaunchText implements TextPresenter<PrimaryLaunchHandOver> {

    @Override
    public Class<PrimaryLaunchHandOver> presents() {
        return PrimaryLaunchHandOver.class;
    }

    @Override
    public String text(PrimaryLaunchHandOver handOver) {
        PrimaryLaunch launch = handOver.launch();
        return "session: " + launch.sessionName() + "\n"
                + "directory: " + launch.directory() + "\n"
                + "settings: " + launch.settings() + "\n"
                + "system prompt: " + launch.systemPrompt() + "\n"
                + "launch key: " + handOver.launchKey() + "\n";
    }
}

package io.github.ythibos.edi.web.health;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.health.TelemetryController.DumpedRecording;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class DumpedRecordingText implements TextPresenter<DumpedRecording> {

    @Override
    public Class<DumpedRecording> presents() {
        return DumpedRecording.class;
    }

    @Override
    public String text(DumpedRecording dumped) {
        return "The flight recording is written to " + dumped.file() + "; open it with JDK Mission Control, or read it with jfr summary or jfr print.\n";
    }
}

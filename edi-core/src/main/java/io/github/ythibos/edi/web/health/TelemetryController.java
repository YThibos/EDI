package io.github.ythibos.edi.web.health;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.adapter.health.FlightRecording;
import io.github.ythibos.edi.application.health.CoreHealthQuery;
import io.github.ythibos.edi.application.health.CoreHealthReport;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class TelemetryController {

    private static final Duration A_WEEK = Duration.ofDays(7);

    private final CoreHealthQuery health;
    private final FlightRecording recording;
    private final Clock clock;

    TelemetryController(CoreHealthQuery health, FlightRecording recording, Clock clock) {
        this.health = health;
        this.recording = recording;
        this.clock = clock;
    }

    @GetMapping("/api/telemetry")
    CoreHealthReport telemetry(Caller caller) {
        caller.requireThePrimaryOrTheCaptain("The core's own health");
        return health.since(clock.now().minus(A_WEEK));
    }

    @PostMapping("/api/telemetry/recordings")
    @ResponseStatus(HttpStatus.CREATED)
    DumpedRecording dump(Caller caller) {
        caller.requirePrimary("edi health --dump-recording");
        return new DumpedRecording(recording.dump().toString());
    }

    record DumpedRecording(String file) {
    }
}

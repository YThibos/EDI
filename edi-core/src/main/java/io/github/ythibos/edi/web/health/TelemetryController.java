package io.github.ythibos.edi.web.health;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.adapter.health.FlightRecording;
import io.github.ythibos.edi.application.health.CoreHealthQuery;
import io.github.ythibos.edi.application.health.CoreHealthReport;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class TelemetryController {

    private static final int LONGEST_PERIOD_IN_DAYS = 35;

    private final CoreHealthQuery health;
    private final FlightRecording recording;
    private final Clock clock;

    TelemetryController(CoreHealthQuery health, FlightRecording recording, Clock clock) {
        this.health = health;
        this.recording = recording;
        this.clock = clock;
    }

    @GetMapping("/api/telemetry")
    CoreHealthReport telemetry(@RequestParam(defaultValue = "7") int days, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("The core's own health");
        return health.since(clock.now().minus(Duration.ofDays(periodOf(days))));
    }

    @PostMapping("/api/telemetry/recordings")
    @ResponseStatus(HttpStatus.CREATED)
    DumpedRecording dump(Caller caller) {
        caller.requirePrimary("edi health --dump-recording");
        return new DumpedRecording(recording.dump().toString());
    }

    private static int periodOf(int days) {
        if (days < 1 || days > LONGEST_PERIOD_IN_DAYS) {
            throw new IllegalArgumentException("The core keeps its health for " + LONGEST_PERIOD_IN_DAYS + " days, so a period runs from 1 to "
                    + LONGEST_PERIOD_IN_DAYS + " days, not " + days + ".");
        }
        return days;
    }

    record DumpedRecording(String file) {
    }
}

package io.github.ythibos.edi.web.health;

import java.util.Locale;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.health.CoreHealthReport;
import io.github.ythibos.edi.application.health.CoreHealthReport.Memory;
import io.github.ythibos.edi.application.health.CoreHealthReport.Recording;
import io.github.ythibos.edi.application.health.CoreHealthReport.Timing;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class CoreHealthText implements TextPresenter<CoreHealthReport> {

    private static final double MEBIBYTE = 1024 * 1024;
    private static final double MILLIS_IN_A_SECOND = 1000;

    private final Moments moments;

    CoreHealthText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<CoreHealthReport> presents() {
        return CoreHealthReport.class;
    }

    @Override
    public String text(CoreHealthReport report) {
        Table timings = new Table("  ").line("The core's health since " + moments.minute(report.since()) + ":");
        report.timings().forEach(timing -> timings.row(timing.measure(), timesOf(timing), "mean " + durationOf(timing.meanMillis()),
                "max " + durationOf(timing.maxMillis())));
        return timings.text() + memoryOf(report.memory()) + recordingOf(report.recording());
    }

    private static String timesOf(Timing timing) {
        return timing.count() + (timing.count() == 1 ? " time" : " times");
    }

    private static String durationOf(double millis) {
        return millis < MILLIS_IN_A_SECOND ? Math.round(millis) + " ms" : String.format(Locale.ROOT, "%.1f s", millis / MILLIS_IN_A_SECOND);
    }

    private static String memoryOf(Memory memory) {
        return "Memory in use: " + mebibytes(memory.meanBytes()) + " on average, " + mebibytes(memory.peakBytes()) + " at its peak.\n";
    }

    private static String recordingOf(Recording recording) {
        return recording.running()
                ? "Flight recording: running, " + mebibytes(recording.bytes()) + " of at most " + mebibytes(recording.mostBytes())
                        + "; edi health --dump-recording writes it out for profiling.\n"
                : "Flight recording: none, as this core runs without a flight recorder.\n";
    }

    private static String mebibytes(double bytes) {
        return Math.round(bytes / MEBIBYTE) + " MiB";
    }
}

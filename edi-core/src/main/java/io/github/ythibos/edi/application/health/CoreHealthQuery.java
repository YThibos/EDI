package io.github.ythibos.edi.application.health;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.health.FlightRecording;
import io.github.ythibos.edi.adapter.health.FlightRecording.RecordingState;
import io.github.ythibos.edi.adapter.store.HealthSamples;
import io.github.ythibos.edi.adapter.store.HealthSamples.HealthSample;
import io.github.ythibos.edi.application.health.CoreHealthReport.Memory;
import io.github.ythibos.edi.application.health.CoreHealthReport.Recording;
import io.github.ythibos.edi.application.health.CoreHealthReport.Timing;

@Service
public class CoreHealthQuery {

    private final HealthSamples samples;
    private final CoreMeasures measures;
    private final FlightRecording recording;
    private final TransactionTemplate transaction;

    public CoreHealthQuery(HealthSamples samples, CoreMeasures measures, FlightRecording recording, TransactionTemplate transaction) {
        this.samples = samples;
        this.measures = measures;
        this.recording = recording;
        this.transaction = transaction;
    }

    public CoreHealthReport since(Instant since) {
        Map<String, HealthSample> measured = recordedAndPendingSince(since);
        HealthSample memory = measured.remove(CoreMeasures.MEMORY_IN_USE);
        return new CoreHealthReport(since, timingsOf(measured.values()), memoryOf(memory), recordingOf(recording.state()));
    }

    private Map<String, HealthSample> recordedAndPendingSince(Instant since) {
        Map<String, HealthSample> measured = new TreeMap<>();
        Stream.concat(transaction.execute(_ -> samples.summedSince(since)).stream(), measures.pending().stream())
                .forEach(sample -> measured.merge(sample.measure(), sample, HealthSample::plus));
        return measured;
    }

    private static List<Timing> timingsOf(Collection<HealthSample> measured) {
        return measured.stream().map(sample -> new Timing(sample.measure(), sample.count(), sample.total() / sample.count(), sample.max())).toList();
    }

    private static Memory memoryOf(HealthSample memory) {
        return memory == null ? new Memory(0, 0) : new Memory(memory.total() / memory.count(), memory.max());
    }

    private static Recording recordingOf(RecordingState state) {
        return new Recording(state.running(), state.bytes(), state.mostBytes());
    }
}

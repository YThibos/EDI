package io.github.ythibos.edi.application.lifecycle;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.dispatch.WorkerPlaces;
import io.github.ythibos.edi.application.worker.StopWorker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class StopTheCore {

    private final WorkerPlaces places;
    private final StopWorker stopWorker;
    private final CaptainsWordEvidence captainsWord;

    public StopTheCore(WorkerPlaces places, StopWorker stopWorker, CaptainsWordEvidence captainsWord) {
        this.places = places;
        this.stopWorker = stopWorker;
        this.captainsWord = captainsWord;
    }

    public List<Worker> stopFor(CoreStop stop, CallingSession calling, Actor actor) {
        if (stop == CoreStop.RESTART) {
            return List.of();
        }
        List<Worker> running = places.running();
        if (running.isEmpty()) {
            return running;
        }
        if (stop != CoreStop.WITH_ITS_WORKERS) {
            throw new Refusal(workersRunning(running) + " (" + running.stream().map(worker -> worker.id().toString())
                    .collect(Collectors.joining(", ")) + "), so the core stays up; edi down --force-stop-workers stops them too, on the captain's word.");
        }
        captainsWord.requireOnTheHome(calling, CaptainWordAct.STOP_RUNNING_WORKERS);
        running.forEach(worker -> stopWorker.stop(new TaskReference.ById(worker.id().task()), actor));
        return running;
    }

    private static String workersRunning(List<Worker> running) {
        return running.size() == 1 ? "1 worker is running" : running.size() + " workers are running";
    }
}

package io.github.ythibos.edi.application.spark;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.SparkStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.Prompt;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.spark.Focus;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkAuthor;
import io.github.ythibos.edi.domain.spark.SparkMessage;

@Service
public class CatchSpark {

    private static final Set<Actor> WHO_STEERS_THE_WORK = Set.of(Actor.PRIMARY, Actor.CAPTAIN);

    private final SparkStore sparks;
    private final TaskStore tasks;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CatchSpark(SparkStore sparks, TaskStore tasks, EventLog events, ProjectRegistry registry, Clock clock, TransactionTemplate transaction) {
        this.sparks = sparks;
        this.tasks = tasks;
        this.events = events;
        this.registry = registry;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Spark caught(SparkRequest request) {
        return transaction.execute(_ -> recorded(request.idea(), request.author(), request.session(), focusOf(request)));
    }

    public Optional<Spark> fromTheCaptainsMessage(UUID session, String message) {
        if (!new Prompt(message).isTypedByTheCaptain()) {
            return Optional.empty();
        }
        return SparkMessage.ideaIn(message)
                .map(idea -> transaction.execute(_ -> recorded(idea, SparkAuthor.CAPTAIN, session, focusOfTheWork())));
    }

    private Spark recorded(String idea, SparkAuthor author, UUID session, Focus focus) {
        Spark spark = Spark.caught(sparks.nextNumber(), idea, author, session, focus, clock.now());
        sparks.add(spark);
        return spark;
    }

    private Focus focusOf(SparkRequest request) {
        if (request.task() != null && request.project() != null) {
            throw new Refusal("A spark is about a task or a project: name one of them, or neither to take the task in focus.");
        }
        if (request.task() != null) {
            return Focus.onTask(tasks.get(request.task()).id());
        }
        if (request.project() != null) {
            return Focus.onProject(registered(request.project()));
        }
        return focusOfTheWork();
    }

    private ProjectName registered(ProjectName project) {
        if (registry.projects().stream().map(Project::name).noneMatch(project::equals)) {
            throw new Refusal(project + " is not a registered project; name one from edi project list.");
        }
        return project;
    }

    private Focus focusOfTheWork() {
        return events.latestTaskActedOnBy(WHO_STEERS_THE_WORK).map(Focus::onTask).orElse(Focus.NONE);
    }
}

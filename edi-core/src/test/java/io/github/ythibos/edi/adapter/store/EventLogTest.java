package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class EventLogTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final TaskId LAMP = new TaskId("lamp-choice");

    @TempDir
    private Path directory;

    @Test
    void keepsEveryEventOfATaskInOrderWithItsPayload() throws IOException {
        TestHome home = TestHome.in(directory);
        Intake intake = new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of());
        home.tasks().insert(Task.queued(LAMP, intake, "", AT));
        EventLog events = home.events();

        events.record(new DomainEvent.TaskAdded(LAMP, intake), Actor.PRIMARY, AT);
        events.record(new DomainEvent.TaskHeld(LAMP, new CaptainHold(HoldKind.DEFERRAL, "later", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT)),
                Actor.PRIMARY, AT.plusSeconds(1));
        events.record(new DomainEvent.DecisionAnswered(LAMP, new DecisionNumber(3),
                new CaptainAnswer("D3 A", new OptionKey('A'), "the LED", AT, AnswerChannel.CHAT)), Actor.PRIMARY, AT.plusSeconds(2));

        assertThat(events.timelineOf(LAMP)).extracting(RecordedEvent::type, RecordedEvent::at)
                .containsExactly(
                        tuple("TaskAdded", AT),
                        tuple("TaskHeld", AT.plusSeconds(1)),
                        tuple("DecisionAnswered", AT.plusSeconds(2)));
        assertThat(home.jdbc().sql("select payload from event where type = 'DecisionAnswered'").query(String.class).single())
                .isEqualTo("{\"number\":\"D3\",\"verbatim\":\"D3 A\",\"option\":\"A\",\"interpretation\":\"the LED\",\"channel\":\"chat\"}");
        assertThat(home.jdbc().sql("select payload from event where type = 'TaskHeld'").query(String.class).single())
                .isEqualTo("{\"kind\":\"deferral\",\"reason\":\"later\",\"until\":\"2026-11-01\"}");
    }
}

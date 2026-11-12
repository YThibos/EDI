package io.github.ythibos.edi.application.spark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.spark.Focus;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkAuthor;
import io.github.ythibos.edi.domain.spark.SparkSorting;
import io.github.ythibos.edi.domain.spark.UnknownSpark;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class SparksTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final TaskId DOOR = new TaskId("fit-door");
    private static final ProjectName HARNESS = new ProjectName("harness");
    private static final UUID SESSION = UUID.fromString("5e55e55e-0000-4000-8000-000000000001");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(WorkerFixtures.queued("fit-lamp", TaskKind.SHIP, "harness", null),
                WorkerFixtures.queued("fit-door", TaskKind.SHIP, "harness", null)));
    }

    @Test
    void catchesASparkByCommandWithItsTimeAuthorSessionAndTheTaskThePrimaryOrTheCaptainActedOnLast() {
        actOn(LAMP, Actor.CAPTAIN);
        actOn(DOOR, Actor.PRIMARY);
        actOn(LAMP, Actor.worker(WorkerId.first(LAMP)));
        home.clock().advance(Duration.ofMinutes(5));

        Spark caught = home.catchSpark().caught(new SparkRequest("  Ask the harbour master about tide tables.\n", SparkAuthor.PRIMARY, SESSION, null, null));

        assertThat(caught).isEqualTo(new Spark(1, "Ask the harbour master about tide tables.", SparkAuthor.PRIMARY, SESSION, Focus.onTask(DOOR),
                TestHome.START.plus(Duration.ofMinutes(5)), null));
        assertThat(home.sortSpark().inbox().open()).containsExactly(caught);
    }

    @Test
    void catchesASparkAboutTheTaskOrProjectTheCommandNames() {
        actOn(DOOR, Actor.PRIMARY);

        Spark onTheLamp = home.catchSpark().caught(new SparkRequest("Brass suits it.", SparkAuthor.CAPTAIN, null, TaskReference.parse("fit-lamp"), null));
        Spark onTheHarness = home.catchSpark().caught(new SparkRequest("Paint it blue.", SparkAuthor.PRIMARY, SESSION, null, HARNESS));

        assertThat(onTheLamp.focus()).isEqualTo(Focus.onTask(LAMP));
        assertThat(onTheLamp.author()).isEqualTo(SparkAuthor.CAPTAIN);
        assertThat(onTheLamp.session()).isNull();
        assertThat(onTheHarness.focus()).isEqualTo(Focus.onProject(HARNESS));
        assertThat(onTheHarness.number()).isEqualTo(2);
    }

    @Test
    void catchesASparkWithNoFocusWhenNobodyHasActedOnATaskYet() {
        assertThat(home.catchSpark().caught(new SparkRequest("A lighthouse.", SparkAuthor.PRIMARY, SESSION, null, null)).focus()).isEqualTo(Focus.NONE);
    }

    @Test
    void refusesASparkWithoutTextOrAboutBothATaskAndAProjectOrAnUnregisteredProject() {
        assertThatThrownBy(() -> home.catchSpark().caught(new SparkRequest("  ", SparkAuthor.PRIMARY, SESSION, null, null)))
                .isInstanceOf(Refusal.class).hasMessage("A spark says something: give the idea's text.");
        assertThatThrownBy(() -> home.catchSpark().caught(new SparkRequest("Both.", SparkAuthor.PRIMARY, SESSION, TaskReference.parse("fit-lamp"), HARNESS)))
                .isInstanceOf(Refusal.class).hasMessageStartingWith("A spark is about a task or a project");
        assertThatThrownBy(() -> home.catchSpark().caught(new SparkRequest("Elsewhere.", SparkAuthor.PRIMARY, SESSION, null, new ProjectName("garden"))))
                .isInstanceOf(Refusal.class).hasMessage("garden is not a registered project; name one from edi project list.");
        assertThat(home.sortSpark().inbox().open()).isEmpty();
    }

    @Test
    void catchesTheCaptainsMessageThatOpensWithSparkAsTheCaptainsSpark() {
        actOn(LAMP, Actor.PRIMARY);

        Spark caught = home.catchSpark().fromTheCaptainsMessage(SESSION, "Spark: a fog horn on the pier\nwith a timer").orElseThrow();
        Spark lowerCase = home.catchSpark().fromTheCaptainsMessage(SESSION, "  spark:   paint the hull").orElseThrow();

        assertThat(caught).isEqualTo(new Spark(1, "a fog horn on the pier\nwith a timer", SparkAuthor.CAPTAIN, SESSION, Focus.onTask(LAMP), TestHome.START,
                null));
        assertThat(lowerCase.text()).isEqualTo("paint the hull");
    }

    @Test
    void catchesNoSparkFromAnOrdinaryMessageAnEmptySparkANotificationOrEdisOwnText() {
        List<String> messages = List.of("Fit the lamp.", "Spark:   ", "A spark: not at the start", "SPARK: shouting",
                "<task-notification>Spark: from a job</task-notification>", OperationalText.marked("Spark: from EDI"));

        messages.forEach(message -> assertThat(home.catchSpark().fromTheCaptainsMessage(SESSION, message)).as(message).isEmpty());
        assertThat(home.sortSpark().inbox().open()).isEmpty();
    }

    @Test
    void sortsASparkIntoTheBacklogWithTheItemsKey() {
        Spark caught = caught("A tide table.");
        home.clock().advance(Duration.ofHours(1));

        Spark sorted = home.sortSpark().backlog(caught.number(), WorkKey.parse("EDI-190"));

        assertThat(sorted.sorting()).isEqualTo(SparkSorting.backlogged(WorkKey.parse("EDI-190"), TestHome.START.plus(Duration.ofHours(1))));
        assertThat(home.sortSpark().inbox().open()).isEmpty();
    }

    @Test
    void foldsASparkIntoAnExistingItem() {
        Spark caught = caught("A brighter lamp.");
        Spark other = caught("A quieter horn.");

        Spark sorted = home.sortSpark().foldInto(caught.number(), WorkKey.parse("EDI-120"));

        assertThat(sorted.sorting()).isEqualTo(SparkSorting.foldedInto(WorkKey.parse("EDI-120"), TestHome.START));
        assertThat(home.sortSpark().inbox().open()).containsExactly(other);
    }

    @Test
    void dropsASparkWithItsReasonAndOnlyWithOne() {
        Spark caught = caught("A second lighthouse.");

        assertThatThrownBy(() -> home.sortSpark().drop(caught.number(), " "))
                .isInstanceOf(Refusal.class).hasMessage("A dropped spark says why it was dropped: give the reason.");
        Spark sorted = home.sortSpark().drop(caught.number(), " One is enough. ");

        assertThat(sorted.sorting()).isEqualTo(SparkSorting.dropped("One is enough.", TestHome.START));
        assertThat(home.sortSpark().inbox().open()).isEmpty();
    }

    @Test
    void sortsASparkOnceAndRefusesANumberNoSparkCarries() {
        Spark caught = caught("A bell.");
        home.sortSpark().drop(caught.number(), "We have one.");

        assertThatThrownBy(() -> home.sortSpark().backlog(caught.number(), WorkKey.parse("EDI-191")))
                .isInstanceOf(Refusal.class).hasMessage("Spark 1 was already dropped; a spark is sorted once.");
        assertThatThrownBy(() -> home.sortSpark().foldInto(7, WorkKey.parse("EDI-191")))
                .isInstanceOf(UnknownSpark.class).hasMessage("No spark is numbered 7; edi spark list shows the open ones.");
    }

    private void actOn(TaskId task, Actor actor) {
        home.inOneTransaction(() -> home.events().record(new TaskNoted(task, "Looked at it."), actor, home.clock().now()));
    }

    private Spark caught(String idea) {
        return home.catchSpark().caught(new SparkRequest(idea, SparkAuthor.PRIMARY, SESSION, null, null));
    }
}

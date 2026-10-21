package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.MemoryCheckStore.CheckedMemory;
import io.github.ythibos.edi.domain.authority.Authorisation;
import io.github.ythibos.edi.domain.authority.Passage;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class PresenceAndMemoryStoresTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY);
    }

    @Test
    void keepsOnePresenceAndReplacesItWholly() {
        Presence away = Presence.presentSince(TestHome.START).away(new Mandate("Carry on with the lamp.", 2), TestHome.START.plusSeconds(60));
        Presence demo = Presence.presentSince(TestHome.START).demo(TestHome.START.plusSeconds(120), TestHome.START.plus(Duration.ofHours(1)));

        assertThat(home.presenceStore().current()).isEmpty();
        home.presenceStore().save(away);
        assertThat(home.presenceStore().current()).contains(away);
        home.presenceStore().save(demo);
        assertThat(home.presenceStore().current()).contains(demo);
    }

    @Test
    void keepsTypedPassagesAsDigestsAndTheCaptainsWordPerSession() {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, true);
        home.addTask().add(Intakes.ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);
        home.captainWords().recordTyped(session, Passage.allTypedIn("Drop the lamp.\nAnd the horn."), TestHome.START);
        Authorisation word = new Authorisation(session, new TaskId("fit-lamp"), "Drop the lamp.", TestHome.START);
        home.captainWords().save(word);

        assertThat(home.captainWords().wasTyped(session, Passage.of("and the horn."))).isTrue();
        assertThat(home.captainWords().wasTyped(session, Passage.of("Drop the horn."))).isFalse();
        assertThat(home.captainWords().wasTyped(UUID.randomUUID(), Passage.of("Drop the lamp."))).isFalse();
        assertThat(home.jdbc().sql("select digest from typed_passage").query(String.class).list()).noneMatch(digest -> digest.contains("lamp"));
        assertThat(home.captainWords().heardIn(session)).containsExactly(word);
    }

    @Test
    void keepsEachDailyMemoryCheck() {
        MemoryCheckStore checks = new MemoryCheckStore(home.jdbc());
        CheckedMemory first = new CheckedMemory(TestHome.START, 900, 1_000);
        CheckedMemory second = new CheckedMemory(TestHome.START.plus(Duration.ofDays(1)), 1_100, 1_000);

        assertThat(checks.latest()).isEmpty();
        checks.record(first);
        checks.record(second);

        assertThat(checks.latest()).contains(second);
        assertThat(second.overBudget()).isTrue();
        assertThat(first.overBudget()).isFalse();
    }
}

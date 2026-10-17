package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;

class TrackedProjectTest {

    private static final WorkKey COORDINATION = WorkKey.parse("OPS-1");
    private static final TrackedProject SERVICE = new TrackedProject(new ProjectName("service"), TrackerKind.JIRA, new ProjectKey("SVC"),
            Set.of(COORDINATION));

    @Test
    void ownsItsOwnKeysAndTheEvergreenTicketsOfAnExternalTracker() {
        assertThatCode(() -> SERVICE.requireOwns(WorkKey.parse("SVC-40"))).doesNotThrowAnyException();
        assertThatCode(() -> SERVICE.requireOwns(COORDINATION)).doesNotThrowAnyException();
        assertThat(SERVICE.isEvergreen(COORDINATION)).isTrue();
        assertThat(SERVICE.isEvergreen(WorkKey.parse("SVC-40"))).isFalse();
    }

    @Test
    void refusesAnotherProjectsKey() {
        assertThatThrownBy(() -> SERVICE.requireOwns(WorkKey.parse("HRN-3"))).isInstanceOf(Refusal.class)
                .hasMessage("HRN-3 is not a service key: its work is keyed SVC-<n> or on an evergreen ticket.");
        TrackedProject harness = new TrackedProject(new ProjectName("harness"), TrackerKind.EDI, new ProjectKey("HRN"), Set.of(COORDINATION));
        assertThatThrownBy(() -> harness.requireOwns(COORDINATION)).isInstanceOf(Refusal.class)
                .hasMessage("OPS-1 is not a harness key: its work is keyed HRN-<n>.");
    }

    @Test
    void takesAnyKeyWithoutATrackerAndNeedsAProjectKeyWithOne() {
        TrackedProject untracked = new TrackedProject(new ProjectName("garden"), TrackerKind.NONE, null, Set.of());

        assertThatCode(() -> untracked.requireOwns(WorkKey.parse("HRN-3"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new TrackedProject(new ProjectName("garden"), TrackerKind.EDI, null, Set.of())).isInstanceOf(Refusal.class)
                .hasMessage("garden books its work on edi but names no projectKey in the project registry.");
    }
}

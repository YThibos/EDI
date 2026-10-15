package io.github.ythibos.edi.adapter.fork;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class TailTagsTest {

    @Test
    void stripsEveryTrailingTag() {
        ForkTags tags = TailTags.of("Tune the horn (repo: lighthouse) (kind: ship) (priority: 2) (since 2026-08-03)"
                + " (hold: waiting) (hold-kind: captain) (hold-until: 2026-11-01)");

        assertThat(tags).isEqualTo(new ForkTags("Tune the horn", "ship", "lighthouse", List.of(), LocalDate.of(2026, 8, 3), null, 2,
                new ForkHold("waiting", "captain", LocalDate.of(2026, 11, 1))));
    }

    @Test
    void leavesAParenthesisInTheMiddleOfTheTitle() {
        ForkTags tags = TailTags.of("SCOUT: read the tables (for the harbour) and report (repo: harbour) (since 2026-09-20)");

        assertThat(tags.title()).isEqualTo("SCOUT: read the tables (for the harbour) and report");
        assertThat(tags.kind()).isEqualTo("scout");
    }

    @Test
    void stopsAtATrailingParenthesisThatIsNoTag() {
        ForkTags tags = TailTags.of("Fix the gate (urgent) (repo: harbour)");

        assertThat(tags.title()).isEqualTo("Fix the gate (urgent)");
        assertThat(tags.repo()).isEqualTo("harbour");
    }

    @Test
    void readsDependenciesBeforeAndAfterTheTagsWithTheirReasons() {
        ForkTags tags = TailTags.of("Count the gulls blocked-by: lantern (repo: harbour) (since 2026-08-04)"
                + " blocked-by: bird-census - waits on the census (spring)");

        assertThat(tags.title()).isEqualTo("Count the gulls");
        assertThat(tags.dependencies()).containsExactly(
                new ForkDependency("blocked-by", "lantern", null),
                new ForkDependency("blocked-by", "bird-census", "waits on the census (spring)"));
    }

    @Test
    void keepsTheRightmostOfARepeatedTag() {
        assertThat(TailTags.of("Paint (repo: left) (repo: right)").repo()).isEqualTo("right");
    }

    @Test
    void readsTheClosingDateOfADoneEntry() {
        assertThat(TailTags.of("Replace the pole (merged 2026-09-30)").closed()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(TailTags.of("Report it (reported 2026-09-29)").closed()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void readsTheKindFromALeadingWordOnlyWhenNoTagNamesIt() {
        assertThat(TailTags.of("DOCS-ONLY: the handbook").kind()).isEqualTo("docs");
        assertThat(TailTags.of("PERSISTENT SECONDMATE keeps the mast").kind()).isEqualTo("secondmate");
        assertThat(TailTags.of("SHIPPING the crates").kind()).isNull();
        assertThat(TailTags.of("SHIP: it (kind: task)").kind()).isEqualTo("task");
    }

    @Test
    void readsAProjectSetTag() {
        assertThat(TailTags.of("Ship both (lighthouse + repo: harbour)").repo()).isEqualTo("harbour");
        assertThat(TailTags.of("Ship both ( repo: harbour)").repo()).isNull();
    }

    @Test
    void decodesAnEncodedHoldReasonAndKeepsAnythingElse() {
        assertThat(HoldReason.decoded("fm-hold-v1:V2hpY2ggYmVhY29uIGNvbG91cj8=")).isEqualTo("Which beacon colour?");
        assertThat(HoldReason.decoded("fm-hold-v1:not base64!")).isEqualTo("fm-hold-v1:not base64!");
        assertThat(HoldReason.decoded("fm-hold-v1:V2hpY2ggYmVhY29uIGNvbG91cj8")).isEqualTo("fm-hold-v1:V2hpY2ggYmVhY29uIGNvbG91cj8");
        assertThat(HoldReason.decoded("plain words")).isEqualTo("plain words");
    }

    @Test
    void ignoresInvalidDatesAndPriorities() {
        ForkTags tags = TailTags.of("Odd (since 2026-13-45) (priority: 7)");

        assertThat(tags.title()).isEqualTo("Odd (since 2026-13-45) (priority: 7)");
    }
}

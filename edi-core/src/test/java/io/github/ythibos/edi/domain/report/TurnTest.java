package io.github.ythibos.edi.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TurnTest {

    private static final TurnItem FITTED = new TurnItem(List.of("harness"), "Fitted the lamp");
    private static final TurnItem POLISHED = new TurnItem(List.of(), "Polished the lamp");
    private static final TurnItem TEST = new TurnItem(List.of("harness"), "Test the lamp");

    @Test
    void keepsItsItemsInTheOrderTheyWereRecordedAndTheLatestWordingOfEachTask() {
        Turn first = new Turn(List.of(FITTED), List.of(), Map.of("fit-lamp", new TaskWording("Fit the lamp", null, null)));
        Turn second = new Turn(List.of(POLISHED), List.of(TEST),
                Map.of("fit-lamp", new TaskWording(null, "fitted", null), "ring-bell", new TaskWording("Ring the bell", null, null)));

        Turn recorded = first.followedBy(second);

        assertThat(recorded.done()).containsExactly(FITTED, POLISHED);
        assertThat(recorded.next()).containsExactly(TEST);
        assertThat(recorded.tasks()).containsOnly(Map.entry("fit-lamp", new TaskWording(null, "fitted", null)),
                Map.entry("ring-bell", new TaskWording("Ring the bell", null, null)));
    }

    @Test
    void recordsNothingOnlyWithoutAnyItemOrWording() {
        assertThat(Turn.NONE.recordsNothing()).isTrue();
        assertThat(new Turn(List.of(), List.of(TEST), Map.of()).recordsNothing()).isFalse();
        assertThat(new Turn(List.of(), List.of(), Map.of("fit-lamp", new TaskWording(null, "fitted", null))).recordsNothing()).isFalse();
    }
}

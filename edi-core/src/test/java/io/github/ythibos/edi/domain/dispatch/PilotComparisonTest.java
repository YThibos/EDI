package io.github.ythibos.edi.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.task.TaskId;

class PilotComparisonTest {

    @Test
    void isReadyAfterTenPlannedShips() {
        assertThat(new PilotComparison(ships(9, 1_000L, 1, 2, 0), List.of()).isReady()).isFalse();
        assertThat(new PilotComparison(ships(10, 1_000L, 1, 2, 0), List.of()).isReady()).isTrue();
    }

    @Test
    void comparesTheAveragesAndRecommendsTheRuleWhenPlanningNeededNoMoreReview() {
        PilotComparison comparison = new PilotComparison(ships(10, 3_000_000L, 1, 2, 0), ships(4, 2_000_000L, 3, 5, 1));

        DecisionCard card = comparison.card();

        assertThat(card.title()).isEqualTo("Make research before implementation the rule?");
        assertThat(card.context()).isEqualTo("""
                Research first planned 10 ships; 4 comparable unplanned ships ran without it.
                Average per ship, with research first against without:
                total tokens, research included: 3,000,000 against 2,000,000;
                review rounds: 1.0 against 3.0;
                findings at review: 2.0 against 5.0;
                review fixes after delivery: 0.0 against 1.0.
                A ship whose figure is unknown is left out of that average.""");
        assertThat(card.options()).extracting(DecisionOption::key).containsExactly(new OptionKey('A'), new OptionKey('B'), new OptionKey('C'));
        assertThat(card.recommendation().key()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void recommendsSwitchingItOffWhenPlannedShipsNeededMoreRework() {
        PilotComparison comparison = new PilotComparison(ships(10, 3_000L, 2, 2, 2), ships(3, 2_000L, 2, 2, 0));

        assertThat(comparison.card().recommendation().key()).isEqualTo(new OptionKey('B'));
    }

    @Test
    void recommendsPilotingFurtherWithoutAnythingToCompareAgainst() {
        DecisionCard card = new PilotComparison(ships(10, null, null, null, 0), List.of()).card();

        assertThat(card.recommendation().key()).isEqualTo(new OptionKey('C'));
        assertThat(card.context()).contains("total tokens, research included: unknown against unknown;");
    }

    private static List<ShipMeasure> ships(int count, Long tokens, Integer rounds, Integer findings, int reviewFixes) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> new ShipMeasure(new TaskId("ship-" + number), tokens, rounds, findings, reviewFixes)).toList();
    }
}

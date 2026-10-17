package io.github.ythibos.edi.domain.dispatch;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Function;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;

public record PilotComparison(List<ShipMeasure> piloted, List<ShipMeasure> comparable) {

    public static final int PILOTED_SHIPS_TO_DECIDE = 10;

    private static final OptionKey MAKE_IT_THE_RULE = new OptionKey('A');
    private static final OptionKey SWITCH_IT_OFF = new OptionKey('B');
    private static final OptionKey PILOT_FURTHER = new OptionKey('C');
    private static final String UNKNOWN = "unknown";

    public PilotComparison {
        piloted = List.copyOf(piloted);
        comparable = List.copyOf(comparable);
    }

    public boolean isReady() {
        return piloted.size() >= PILOTED_SHIPS_TO_DECIDE;
    }

    public DecisionCard card() {
        return new DecisionCard("Make research before implementation the rule?", context(), options(), recommendation());
    }

    private String context() {
        return String.join("\n",
                "Research first planned " + piloted.size() + " ships; " + comparable.size() + " comparable unplanned ships ran without it.",
                "Average per ship, with research first against without:",
                "total tokens, research included: " + compared(ShipMeasure::tokensIfKnown, 0) + ";",
                "review rounds: " + compared(ShipMeasure::reviewRoundsIfKnown, 1) + ";",
                "findings at review: " + compared(ShipMeasure::findingsIfKnown, 1) + ";",
                "review fixes after delivery: " + compared(ShipMeasure::reviewFixesCounted, 1) + ".",
                "A ship whose figure is unknown is left out of that average.");
    }

    private List<DecisionOption> options() {
        return List.of(
                new DecisionOption(MAKE_IT_THE_RULE, "Make research first the rule: keep researchFirst on for implementation-unplanned and for bugs"
                        + " whose cause is unknown"),
                new DecisionOption(SWITCH_IT_OFF, "Switch research first off again: unplanned work goes straight to its implementation worker"),
                new DecisionOption(PILOT_FURTHER, "Pilot " + PILOTED_SHIPS_TO_DECIDE + " more ships before deciding"));
    }

    private Recommendation recommendation() {
        if (comparable.isEmpty()) {
            return new Recommendation(PILOT_FURTHER, "no unplanned ship finished without research, so there is nothing to compare against yet");
        }
        if (isNoWorse(ShipMeasure::reviewRoundsIfKnown) && isNoWorse(ShipMeasure::findingsIfKnown) && isNoWorse(ShipMeasure::reviewFixesCounted)) {
            return new Recommendation(MAKE_IT_THE_RULE, "the planned ships needed no more review rounds, findings or review fixes than the unplanned ones");
        }
        return new Recommendation(SWITCH_IT_OFF, "the planned ships needed more review or rework than the unplanned ones, so the research did not pay");
    }

    private String compared(Function<ShipMeasure, OptionalDouble> figure, int decimals) {
        return written(averageOf(piloted, figure), decimals) + " against " + written(averageOf(comparable, figure), decimals);
    }

    private boolean isNoWorse(Function<ShipMeasure, OptionalDouble> figure) {
        OptionalDouble planned = averageOf(piloted, figure);
        OptionalDouble unplanned = averageOf(comparable, figure);
        return planned.isEmpty() || unplanned.isEmpty() || planned.getAsDouble() <= unplanned.getAsDouble();
    }

    private static OptionalDouble averageOf(List<ShipMeasure> ships, Function<ShipMeasure, OptionalDouble> figure) {
        return ships.stream().map(figure).filter(OptionalDouble::isPresent).mapToDouble(OptionalDouble::getAsDouble).average();
    }

    private static String written(OptionalDouble average, int decimals) {
        return average.isEmpty() ? UNKNOWN : String.format(Locale.ROOT, "%,." + decimals + "f", average.getAsDouble());
    }
}

package io.github.ythibos.edi.testing;

import java.util.List;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;

public final class Cards {

    private Cards() {
    }

    public static DecisionCard card(String title) {
        return new DecisionCard(
                title,
                "Both work; one is simpler.",
                List.of(new DecisionOption(new OptionKey('A'), "The simple way"), new DecisionOption(new OptionKey('B'), "The thorough way")),
                new Recommendation(new OptionKey('A'), "it is enough"));
    }
}

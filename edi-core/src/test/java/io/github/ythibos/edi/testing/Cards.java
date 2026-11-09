package io.github.ythibos.edi.testing;

import java.util.List;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
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

    public static DecisionCard urgent(String title) {
        DecisionCard card = card(title);
        return new DecisionCard(card.title(), card.context(), card.options(), card.recommendation(), true);
    }

    public static DecisionCard authorising(String title, CaptainWordAct act) {
        return new DecisionCard(
                title,
                "The work has not landed.",
                List.of(new DecisionOption(new OptionKey('A'), "Go ahead", act), new DecisionOption(new OptionKey('B'), "Keep it")),
                new Recommendation(new OptionKey('B'), "it may still be needed"));
    }
}

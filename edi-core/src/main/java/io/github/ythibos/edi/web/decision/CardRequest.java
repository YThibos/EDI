package io.github.ythibos.edi.web.decision;

import static io.github.ythibos.edi.web.RequestValues.orEmpty;

import java.util.List;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;

public record CardRequest(String title, String context, List<Option> options, Recommend recommend) {

    public DecisionCard card() {
        return new DecisionCard(
                required(title, "title"),
                orEmpty(context),
                options == null ? List.of() : options.stream().map(Option::option).toList(),
                required(recommend, "recommendation").recommendation());
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("The card needs its " + name + ".");
        }
        return value;
    }

    public record Option(String key, String text, String authorises) {

        DecisionOption option() {
            return new DecisionOption(OptionKey.parse(required(key, "option key")), required(text, "option text"),
                    authorises == null ? null : CaptainWordAct.authorisableByAnOption(authorises));
        }
    }

    public record Recommend(String key, String why) {

        Recommendation recommendation() {
            return new Recommendation(OptionKey.parse(required(key, "recommended option")), required(why, "reason for the recommendation"));
        }
    }
}

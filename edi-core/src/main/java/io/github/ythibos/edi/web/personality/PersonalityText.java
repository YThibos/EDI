package io.github.ythibos.edi.web.personality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.personality.PersonalityInForce;
import io.github.ythibos.edi.domain.personality.AddressPlacement;
import io.github.ythibos.edi.domain.personality.Addressing;
import io.github.ythibos.edi.domain.personality.Likelihood;
import io.github.ythibos.edi.domain.personality.MessageContext;
import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
public class PersonalityText implements TextPresenter<PersonalityInForce> {

    @Override
    public Class<PersonalityInForce> presents() {
        return PersonalityInForce.class;
    }

    @Override
    public String text(PersonalityInForce personality) {
        return String.join("\n", lines(personality)) + "\n";
    }

    public List<String> lines(PersonalityInForce personality) {
        List<String> lines = new ArrayList<>(List.of(speakingLine(personality)));
        lines.addAll(choiceToOffer(personality));
        lines.add(addressLine(personality.address(), personality.speaking().addressing()));
        lines.add("Bad news: " + levelsOf(personality.speaking().forBadNews()) + ", and never \"Acknowledged\".");
        return lines;
    }

    private static String speakingLine(PersonalityInForce personality) {
        String heading = personality.demoHolds() ? "Personality for the demo: " : "Personality: ";
        return heading + levelsOf(personality.speaking()) + "; edi-personality says what each level means.";
    }

    private static List<String> choiceToOffer(PersonalityInForce personality) {
        return personality.captains().isChosen() ? List.of()
                : List.of("No personality is chosen yet, so Balanced applies: offer the captain the presets in this session (edi-personality).");
    }

    private static String addressLine(CaptainAddress address, Addressing addressing) {
        String heading = "Address word \"" + address.word() + "\": ";
        if (addressing.isNever()) {
            return heading + "never.";
        }
        return heading + howOften(addressing) + "; it goes " + where(addressing) + ".";
    }

    private static String levelsOf(Personality personality) {
        return personality.levels().entrySet().stream()
                .map(level -> level.getKey().word() + " " + level.getValue().value())
                .collect(Collectors.joining(", "));
    }

    private static String howOften(Addressing addressing) {
        Map<Likelihood, List<String>> contextsByLikelihood = new LinkedHashMap<>();
        for (MessageContext context : MessageContext.values()) {
            contextsByLikelihood.computeIfAbsent(addressing.in(context), _ -> new ArrayList<>()).add(contextWords(context));
        }
        return contextsByLikelihood.entrySet().stream()
                .map(often -> likelihoodWords(often.getKey()) + " with " + alternatives(often.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String where(Addressing addressing) {
        return addressing.preferredPlace()
                .map(preferred -> "preferably " + placeWords(preferred) + ", else " + placesWords(addressing.places().stream().filter(place -> place != preferred).toList()))
                .orElseGet(() -> placesWords(addressing.places()));
    }

    private static String contextWords(MessageContext context) {
        return switch (context) {
            case BAD_NEWS -> "bad news";
            case DECISION_NEEDED -> "a decision needed";
            case FIRST_MESSAGE -> "the session's first message";
            case GOOD_NEWS -> "good news";
            case STATUS_REPORT -> "a status report";
            case NOTHING_TO_REPORT -> "nothing to report";
        };
    }

    private static String likelihoodWords(Likelihood likelihood) {
        return switch (likelihood) {
            case NEVER -> "never";
            case RARELY -> "rarely";
            case SOMETIMES -> "sometimes";
            case USUALLY -> "usually";
            case ALMOST_ALWAYS -> "almost always";
        };
    }

    private static String alternatives(List<String> words) {
        if (words.size() == 1) {
            return words.getFirst();
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.getLast();
    }

    private static String placeWords(AddressPlacement placement) {
        return switch (placement) {
            case OPENING_THE_MESSAGE -> "at the start of the message";
            case OPENING_A_LATER_SENTENCE -> "at the start of a later sentence";
            case CLOSING_TAG -> "as a closing tag";
        };
    }

    private static String placesWords(List<AddressPlacement> places) {
        return alternatives(places.stream().map(PersonalityText::placeWords).toList());
    }
}

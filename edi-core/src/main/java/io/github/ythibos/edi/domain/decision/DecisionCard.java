package io.github.ythibos.edi.domain.decision;

import java.util.List;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;

public record DecisionCard(String title, String context, List<DecisionOption> options, Recommendation recommendation, boolean urgent) {

    public DecisionCard {
        title = title.strip();
        context = context.strip();
        options = List.copyOf(options);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("A decision card has a title.");
        }
        if (options.isEmpty()) {
            throw new IllegalArgumentException("A decision card offers at least one option.");
        }
        if (options.stream().map(DecisionOption::key).distinct().count() < options.size()) {
            throw new IllegalArgumentException("A decision card gives each option its own letter.");
        }
        if (!offers(options, recommendation.key())) {
            throw new IllegalArgumentException("The card recommends option " + recommendation.key() + ", which it does not offer.");
        }
    }

    public DecisionCard(String title, String context, List<DecisionOption> options, Recommendation recommendation) {
        this(title, context, options, recommendation, false);
    }

    public boolean offers(OptionKey key) {
        return offers(options, key);
    }

    public DecisionOption recommendedOption() {
        return optionOf(recommendation.key());
    }

    public DecisionOption optionOf(OptionKey key) {
        return options.stream().filter(option -> option.key().equals(key)).findFirst().orElseThrow();
    }

    public boolean asksTheSameAs(DecisionCard other) {
        return title.equals(other.title);
    }

    public boolean authorises(OptionKey chosen, CaptainWordAct act) {
        return options.stream().anyMatch(option -> option.key().equals(chosen) && option.authorises(act));
    }

    public String wording() {
        return String.join("\n", Stream.concat(Stream.of(title, context, recommendation.why()), options.stream().map(DecisionOption::text)).toList());
    }

    private static boolean offers(List<DecisionOption> options, OptionKey key) {
        return options.stream().anyMatch(option -> option.key().equals(key));
    }
}

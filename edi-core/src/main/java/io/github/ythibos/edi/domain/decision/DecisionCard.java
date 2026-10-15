package io.github.ythibos.edi.domain.decision;

import java.util.List;

public record DecisionCard(String title, String context, List<DecisionOption> options, Recommendation recommendation) {

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

    public boolean offers(OptionKey key) {
        return offers(options, key);
    }

    private static boolean offers(List<DecisionOption> options, OptionKey key) {
        return options.stream().anyMatch(option -> option.key().equals(key));
    }
}

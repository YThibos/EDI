package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

public record ChosenRoute(Route route, String rule) {

    public ChosenRoute {
        Objects.requireNonNull(route, "A chosen route names the model it runs on.");
        Objects.requireNonNull(rule, () -> "The route " + route + " names the rule that chose it.");
    }

    @Override
    public String toString() {
        return route + ", chosen by " + rule;
    }
}

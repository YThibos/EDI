package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

public record ChosenRoute(Route route, String choice) {

    public ChosenRoute {
        Objects.requireNonNull(route, "A chosen route names the model it runs on.");
        Objects.requireNonNull(choice, () -> "The route " + route + " says how it was chosen.");
    }

    public static ChosenRoute chosenBy(Route route, String rule) {
        return new ChosenRoute(route, "chosen by " + rule);
    }

    public static ChosenRoute asAtItsLaunch(Route route) {
        return new ChosenRoute(route, "as at its launch");
    }

    @Override
    public String toString() {
        return route + ", " + choice;
    }
}

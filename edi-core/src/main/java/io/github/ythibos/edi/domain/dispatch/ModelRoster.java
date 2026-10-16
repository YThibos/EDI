package io.github.ythibos.edi.domain.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public record ModelRoster(List<AllowedModel> strongestFirst) {

    public ModelRoster {
        strongestFirst = List.copyOf(strongestFirst);
        if (new HashSet<>(strongestFirst.stream().map(AllowedModel::id).toList()).size() < strongestFirst.size()) {
            throw new IllegalArgumentException("The machine profile allows each model once.");
        }
    }

    public void requireAllows(Route route, String usedBy) {
        AllowedModel model = allowed(route.model(), usedBy);
        if (model.takesEffort() && !route.namesAnEffort()) {
            throw new IllegalArgumentException(usedBy + " runs " + route.model() + " without an effort; name low, medium or high.");
        }
        if (!model.takesEffort() && route.namesAnEffort()) {
            throw new IllegalArgumentException(usedBy + " gives " + route.model() + " an effort, but that model ignores the effort flag.");
        }
    }

    public boolean isWeaker(ModelId model, ModelId than) {
        return rank(model) > rank(than);
    }

    private AllowedModel allowed(ModelId id, String usedBy) {
        return strongestFirst.stream()
                .filter(model -> model.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(usedBy + " runs " + id
                        + ", which the machine profile does not allow; it allows " + allowedNames() + "."));
    }

    private int rank(ModelId id) {
        return strongestFirst.stream().map(AllowedModel::id).toList().indexOf(id);
    }

    private String allowedNames() {
        return strongestFirst.isEmpty()
                ? "no model at all"
                : strongestFirst.stream().map(model -> model.id().value()).collect(Collectors.joining(", "));
    }

    public record AllowedModel(ModelId id, boolean takesEffort) {
    }
}

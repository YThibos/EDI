package io.github.ythibos.edi.adapter.fork;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForkDecisionCard(String title, String context, List<String> projects, List<ForkOption> options, ForkRecommendation recommend) {

    public ForkDecisionCard {
        projects = projects == null ? List.of() : List.copyOf(projects);
        options = options == null ? List.of() : List.copyOf(options);
    }
}

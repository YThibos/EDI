package io.github.ythibos.edi.domain.plan;

import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;

public record PlanHeading(WorkKey key, String title, ProjectName project, String report, String go, int capacity, EffortLevel effort) {

    public PlanHeading {
        Objects.requireNonNull(key, "A plan is known by its umbrella work key.");
        Objects.requireNonNull(project, "The plan " + key + " names the project its slices work on.");
        title = oneLine(title, "The plan " + key + " has a title of one line.");
        report = oneLine(report, "The plan " + key + " names the report it came from.");
        go = oneLine(go, "The plan " + key + " names the decision that gave the go.");
        if (capacity < 1) {
            throw new IllegalArgumentException("The plan " + key + " works on at least one slice at a time: " + capacity);
        }
    }

    private static String oneLine(String text, String sentence) {
        String line = Objects.requireNonNullElse(text, "").strip();
        if (line.isEmpty() || line.contains("\n")) {
            throw new IllegalArgumentException(sentence);
        }
        return line;
    }
}

package io.github.ythibos.edi.web.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.domain.report.TaskWording;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.report.TurnItem;
import tools.jackson.databind.JsonNode;

public final class TurnDocument {

    private static final List<String> PARTS = List.of("done", "next", "tasks");
    private static final List<String> ITEM_FIELDS = List.of("projects", "text");
    private static final List<String> WORDING_FIELDS = List.of("projects", "state", "text");

    private TurnDocument() {
    }

    public static Turn read(JsonNode turn) {
        requireObject(turn, "The turn is one JSON object with done, next and tasks.");
        refuseOtherFields(turn, PARTS, "The turn takes only done, next and tasks");
        return new Turn(itemsOf(turn.get("done"), "done"), itemsOf(turn.get("next"), "next"), wordingsOf(turn.get("tasks")));
    }

    private static void requireObject(JsonNode node, String sentence) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(sentence);
        }
    }

    private static void refuseOtherFields(JsonNode node, List<String> fields, String takesOnly) {
        List<String> others = node.propertyNames().stream().filter(name -> !fields.contains(name)).sorted().toList();
        if (!others.isEmpty()) {
            throw new IllegalArgumentException(takesOnly + ", not " + String.join(", ", others) + ".");
        }
    }

    private static List<TurnItem> itemsOf(JsonNode items, String part) {
        if (isAbsent(items)) {
            return List.of();
        }
        if (!items.isArray()) {
            throw new IllegalArgumentException("The turn's " + part + " is a list of items.");
        }
        return items.values().stream().map(item -> itemOf(item, part)).toList();
    }

    private static Map<String, TaskWording> wordingsOf(JsonNode wordings) {
        if (isAbsent(wordings)) {
            return Map.of();
        }
        requireObject(wordings, "The turn's tasks are an object keyed by task id.");
        Map<String, TaskWording> byTask = new LinkedHashMap<>();
        wordings.properties().forEach(wording -> byTask.put(wording.getKey(), wordingOf(wording.getKey(), wording.getValue())));
        return byTask;
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static TurnItem itemOf(JsonNode item, String part) {
        String where = "an item of the turn's " + part;
        requireObject(item, "Each item of the turn's " + part + " is an object with projects and text.");
        refuseOtherFields(item, ITEM_FIELDS, capitalised(where) + " takes only projects and text");
        List<String> projects = isAbsent(item.get("projects")) ? List.of() : projectsOf(item.get("projects"), where);
        return new TurnItem(projects, oneLine(item.get("text"), "The text of " + where + " is one non-empty line."));
    }

    private static TaskWording wordingOf(String task, JsonNode wording) {
        String where = "the turn's wording of " + task;
        requireObject(wording, capitalised(where) + " is an object with text, state and projects.");
        refuseOtherFields(wording, WORDING_FIELDS, capitalised(where) + " takes only text, state and projects");
        return new TaskWording(
                lineIfGiven(wording.get("text"), "The text of " + where + " is one non-empty line."),
                lineIfGiven(wording.get("state"), "The state of " + where + " is one non-empty line."),
                isAbsent(wording.get("projects")) ? null : projectsOf(wording.get("projects"), where));
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static List<String> projectsOf(JsonNode projects, String where) {
        if (!projects.isArray()) {
            throw new IllegalArgumentException("The projects of " + where + " are a list.");
        }
        return projects.values().stream().map(project -> oneLine(project, "Each project of " + where + " is one non-empty line.")).toList();
    }

    private static String oneLine(JsonNode text, String sentence) {
        if (text == null || !text.isString() || !isOneNonEmptyLine(text.stringValue())) {
            throw new IllegalArgumentException(sentence);
        }
        return text.stringValue();
    }

    private static String lineIfGiven(JsonNode text, String sentence) {
        return isAbsent(text) ? null : oneLine(text, sentence);
    }

    private static boolean isOneNonEmptyLine(String text) {
        return !text.isEmpty() && text.indexOf('\n') < 0 && text.indexOf('\r') < 0;
    }
}

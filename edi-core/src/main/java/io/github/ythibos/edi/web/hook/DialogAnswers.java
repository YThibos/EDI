package io.github.ythibos.edi.web.hook;

import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.domain.authority.AnsweredQuestion;
import tools.jackson.databind.JsonNode;

final class DialogAnswers {

    private static final String ANSWERS = "answers";
    private static final String ANNOTATIONS = "annotations";

    private DialogAnswers() {
    }

    static List<AnsweredQuestion> in(JsonNode payload) {
        JsonNode given = answered(payload.path("tool_response")) ? payload.path("tool_response") : payload.path("tool_input");
        JsonNode annotations = given.path(ANNOTATIONS);
        return given.path(ANSWERS).properties().stream()
                .filter(DialogAnswers::isAChoice)
                .map(answer -> new AnsweredQuestion(answer.getKey(), answer.getValue().asString(),
                        annotations.path(answer.getKey()).path("notes").asString("")))
                .toList();
    }

    static boolean areFilledIn(JsonNode toolInput) {
        return answered(toolInput);
    }

    private static boolean answered(JsonNode node) {
        return node.path(ANSWERS).isObject() && !node.path(ANSWERS).isEmpty();
    }

    private static boolean isAChoice(Map.Entry<String, JsonNode> answer) {
        return !answer.getKey().isBlank() && answer.getValue().isString() && !answer.getValue().asString().isBlank();
    }
}

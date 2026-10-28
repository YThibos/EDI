package io.github.ythibos.edi.adapter.board;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.board.BoardAttachment;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class LavishResult {

    private static final String CONTEXT_DATA = "\n\nContext data:\n";
    private static final String FEEDBACK = "feedback";
    private static final String ENDED = "ended";
    private static final String DISCONNECTED = "browser_disconnected";

    private final JsonMapper json;

    LavishResult(JsonMapper json) {
        this.json = json;
    }

    BoardFeedback feedbackIn(String output) {
        ObjectNode result = ToonReader.read(output);
        if (result.has("error")) {
            return new BoardFeedback.Failed(result.path("error").asString() + codeOf(result));
        }
        JsonNode session = result.path("session");
        List<BoardPrompt> prompts = promptsIn(result.path("prompts"));
        boolean ended = session.path("session_ended").asBoolean(false);
        return switch (session.path("status").asString("")) {
            case FEEDBACK -> prompts.isEmpty() ? withoutPrompts(ended) : new BoardFeedback.Answered(prompts, ended);
            case ENDED -> prompts.isEmpty() ? new BoardFeedback.ClosedEmpty() : new BoardFeedback.Answered(prompts, true);
            case DISCONNECTED -> new BoardFeedback.Disconnected();
            case String status -> new BoardFeedback.Failed("Lavish answered with an unknown session status: " + quoted(status) + ".");
        };
    }

    private static String codeOf(JsonNode result) {
        return result.has("code") ? " (" + result.path("code").asString() + ")" : "";
    }

    private static BoardFeedback withoutPrompts(boolean ended) {
        return ended
                ? new BoardFeedback.ClosedEmpty()
                : new BoardFeedback.Failed("Lavish reported feedback without a single prompt, so nothing the captain sent could be read.");
    }

    private static String quoted(String status) {
        return status.isEmpty() ? "none" : status;
    }

    private List<BoardPrompt> promptsIn(JsonNode prompts) {
        return prompts.valueStream().map(this::promptOf).toList();
    }

    private BoardPrompt promptOf(JsonNode prompt) {
        String sent = prompt.path("prompt").asString("");
        int contextStart = sent.lastIndexOf(CONTEXT_DATA);
        String words = contextStart < 0 ? sent : sent.substring(0, contextStart);
        Map<String, String> context = contextStart < 0 ? Map.of() : contextData(sent.substring(contextStart + CONTEXT_DATA.length()));
        return new BoardPrompt(
                prompt.path("uid").asString(""),
                prompt.path("tag").asString(""),
                prompt.path("text").asString(""),
                context.isEmpty() && contextStart >= 0 ? sent : words,
                prompt.path("selector").asString(""),
                context,
                attachmentsIn(prompt.path("attachments")));
    }

    private Map<String, String> contextData(String data) {
        try {
            JsonNode context = json.readTree(data);
            Map<String, String> fields = new LinkedHashMap<>();
            context.properties().forEach(field -> fields.put(field.getKey(), wordsOf(field.getValue())));
            return fields;
        } catch (JacksonException notJson) {
            return Map.of();
        }
    }

    private static String wordsOf(JsonNode value) {
        return value.isArray()
                ? value.valueStream().map(JsonNode::asString).collect(Collectors.joining(", "))
                : value.asString("");
    }

    private static List<BoardAttachment> attachmentsIn(JsonNode attachments) {
        return attachments.valueStream()
                .map(attachment -> new BoardAttachment(attachment.path("path").asString(), attachment.path("mime").asString(""),
                        attachment.path("name").asString("")))
                .toList();
    }
}

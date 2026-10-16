package io.github.ythibos.edi.web.hook;

import java.nio.file.Path;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.worker.GuardToolCall;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.application.worker.WorkerHooks;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.web.access.Caller;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
class HookController {

    private final WorkerHooks hooks;
    private final GuardToolCall guard;
    private final JsonMapper json;

    HookController(WorkerHooks hooks, GuardToolCall guard, JsonMapper json) {
        this.hooks = hooks;
        this.guard = guard;
        this.json = json;
    }

    @PostMapping(value = "/api/hooks/{event}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> hook(@PathVariable String event, @RequestBody JsonNode payload, Caller caller) {
        HookEvent hook = HookEvent.named(event);
        HookAnswer answer = switch (caller) {
            case Caller.WorkerCaller worker -> workerHook(hook, worker.worker(), payload);
            case Caller.Primary _ -> hook == HookEvent.PRE_TOOL_USE ? guard.checkForPrimary(toolCallIn(payload)) : HookAnswer.PROCEED;
        };
        return rendered(answer);
    }

    private HookAnswer workerHook(HookEvent hook, WorkerId worker, JsonNode payload) {
        return switch (hook) {
            case SESSION_START -> hooks.sessionStarted(worker);
            case USER_PROMPT_SUBMIT -> hooks.promptSubmitted(worker, payload.path("prompt").asString(""));
            case PRE_TOOL_USE -> guard.checkForWorker(worker, toolCallIn(payload));
            case STOP -> hooks.stopping(worker);
        };
    }

    private static ToolCall toolCallIn(JsonNode payload) {
        JsonNode input = payload.path("tool_input");
        String file = input.path("file_path").asString(input.path("notebook_path").asString(null));
        String cwd = payload.path("cwd").asString(null);
        return new ToolCall(payload.path("tool_name").asString(""), input.path("command").asString(null), file, cwd == null ? null : Path.of(cwd));
    }

    private ResponseEntity<String> rendered(HookAnswer answer) {
        return switch (answer.verdict()) {
            case PROCEED -> ResponseEntity.noContent().build();
            case BLOCK_THE_STOP -> asJson(json.createObjectNode().put("decision", "block").put("reason", answer.reason()));
            case DENY_THE_TOOL -> asJson(toolDenial(answer.reason()));
        };
    }

    private ObjectNode toolDenial(String reason) {
        ObjectNode output = json.createObjectNode();
        output.putObject("hookSpecificOutput")
                .put("hookEventName", "PreToolUse")
                .put("permissionDecision", "deny")
                .put("permissionDecisionReason", reason);
        return output;
    }

    private ResponseEntity<String> asJson(ObjectNode output) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(output));
    }
}

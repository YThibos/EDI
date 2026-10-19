package io.github.ythibos.edi.web.hook;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.application.primary.SessionStartDigestQuery;
import io.github.ythibos.edi.application.worker.GuardToolCall;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.application.worker.WorkerHooks;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.primary.BackgroundJob;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;
import io.github.ythibos.edi.web.presence.ReturnBriefText;
import io.github.ythibos.edi.web.primary.SessionStartDigestText;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
class HookController {

    private final WorkerHooks workerHooks;
    private final PrimaryHooks primaryHooks;
    private final GuardToolCall guard;
    private final SessionStartDigestQuery digests;
    private final SessionStartDigestText digestText;
    private final ReturnBriefText returnBriefText;
    private final JsonMapper json;

    HookController(WorkerHooks workerHooks, PrimaryHooks primaryHooks, GuardToolCall guard, SessionStartDigestQuery digests,
            SessionStartDigestText digestText, ReturnBriefText returnBriefText, JsonMapper json) {
        this.workerHooks = workerHooks;
        this.primaryHooks = primaryHooks;
        this.guard = guard;
        this.digests = digests;
        this.digestText = digestText;
        this.returnBriefText = returnBriefText;
        this.json = json;
    }

    @PostMapping(value = "/api/hooks/{event}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> hook(@PathVariable String event, @RequestBody JsonNode payload, Caller caller,
            @RequestHeader(value = "EDI-Claude-Pid", required = false) Long claudePid) {
        HookEvent hook = HookEvent.named(event);
        return switch (caller) {
            case Caller.WorkerCaller worker -> rendered(workerHook(hook, worker.worker(), payload));
            case Caller.Primary _, Caller.Standby _ -> primaryHook(hook, payload, claudePid);
            case Caller.Captain _ -> throw new NotPermitted("Hooks come from Claude Code sessions, never from the console.");
        };
    }

    private HookAnswer workerHook(HookEvent hook, WorkerId worker, JsonNode payload) {
        return switch (hook) {
            case SESSION_START -> workerHooks.sessionStarted(worker, optionalSessionIn(payload), textIn(payload, "transcript_path"));
            case USER_PROMPT_SUBMIT -> workerHooks.promptSubmitted(worker, payload.path("prompt").asString(""));
            case PRE_TOOL_USE -> guard.checkForWorker(worker, toolCallIn(payload));
            case STOP -> workerHooks.stopping(worker);
            case STOP_FAILURE -> workerHooks.stopFailed(worker, textIn(payload, "error"), textIn(payload, "transcript_path"));
            case NOTIFICATION -> workerHooks.notified(worker, textIn(payload, "notification_type"));
            case SESSION_END -> workerHooks.sessionEnded(worker, textIn(payload, "reason"));
        };
    }

    private ResponseEntity<String> primaryHook(HookEvent hook, JsonNode payload, Long claudePid) {
        return switch (hook) {
            case SESSION_START -> sessionStarted(sessionIn(payload), claudePid);
            case USER_PROMPT_SUBMIT -> promptSubmitted(sessionIn(payload), payload.path("prompt").asString(""));
            case PRE_TOOL_USE -> rendered(guard.checkForPrimary(toolCallIn(payload), callingSessionIn(payload)));
            case STOP -> rendered(primaryHooks.stopping(sessionIn(payload), backgroundJobsIn(payload)));
            case STOP_FAILURE -> rendered(primaryHooks.stopFailed(sessionIn(payload), textIn(payload, "transcript_path")));
            case NOTIFICATION -> rendered(primaryHooks.notified(sessionIn(payload), textIn(payload, "notification_type")));
            case SESSION_END -> rendered(primaryHooks.sessionEnded(sessionIn(payload)));
        };
    }

    private ResponseEntity<String> sessionStarted(UUID session, Long claudePid) {
        primaryHooks.sessionStarted(session, claudePid);
        return asJson(additionalContext(HookEvent.SESSION_START, digestText.text(digests.digestFor(session))));
    }

    private ResponseEntity<String> promptSubmitted(UUID session, String prompt) {
        return primaryHooks.promptSubmitted(session, prompt)
                .map(brief -> asJson(additionalContext(HookEvent.USER_PROMPT_SUBMIT, returnBriefText.text(brief))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static CallingSession callingSessionIn(JsonNode payload) {
        UUID session = optionalSessionIn(payload);
        return session == null ? CallingSession.UNNAMED : new CallingSession(session);
    }

    private static UUID optionalSessionIn(JsonNode payload) {
        return payload.path("session_id").isString() ? sessionIn(payload) : null;
    }

    private static String textIn(JsonNode payload, String field) {
        return payload.path(field).asString(null);
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

    private static UUID sessionIn(JsonNode payload) {
        String session = payload.path("session_id").asString("");
        try {
            return UUID.fromString(session);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("A hook names its Claude Code session id, and \"" + session + "\" is none.");
        }
    }

    private ObjectNode additionalContext(HookEvent hook, String context) {
        ObjectNode output = json.createObjectNode();
        output.putObject("hookSpecificOutput").put("hookEventName", hook.claudeCodeName()).put("additionalContext", context);
        return output;
    }

    private static List<BackgroundJob> backgroundJobsIn(JsonNode payload) {
        return StreamSupport.stream(payload.path("background_tasks").spliterator(), false)
                .map(job -> new BackgroundJob(job.path("type").asString(""), job.path("status").asString(""), job.path("command").asString("")))
                .toList();
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

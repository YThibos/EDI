package io.github.ythibos.edi.adapter.tracker;

import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.AvailableTransition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class JiraTransitions {

    private static final String API = "/rest/api/2";

    private final JiraCalls calls;
    private final JsonMapper json;

    JiraTransitions(JiraCalls calls, JsonMapper json) {
        this.calls = calls;
        this.json = json;
    }

    String statusOf(TrackerSettings settings, WorkKey key) {
        return standingOf(settings, key, "status", "the status of " + key).status();
    }

    Standing standingOf(TrackerSettings settings, WorkKey key) {
        return standingOf(settings, key, "status,issuetype", "the status and type of " + key);
    }

    List<AvailableTransition> offeredTo(TrackerSettings settings, WorkKey key) {
        return calls.read(settings, API + "/issue/" + key + "/transitions", Map.of(), "the transitions of " + key)
                .path("transitions").valueStream()
                .map(transition -> new AvailableTransition(transition.path("id").asString(), transition.path("name").asString(""),
                        transition.path("to").path("name").asString("")))
                .toList();
    }

    String statusAfter(TrackerSettings settings, WorkKey key, AvailableTransition transition) {
        ObjectNode body = json.createObjectNode();
        body.putObject("transition").put("id", transition.id());
        calls.write(settings, "POST", API + "/issue/" + key + "/transitions", body, "the transition of " + key + " to " + transition.to());
        return statusOf(settings, key);
    }

    private Standing standingOf(TrackerSettings settings, WorkKey key, String fields, String what) {
        JsonNode read = calls.read(settings, API + "/issue/" + key, Map.of("fields", fields), what).path("fields");
        return new Standing(read.path("status").path("name").asString("").strip(), read.path("issuetype").path("name").asString("").strip());
    }

    record Standing(String status, String issueType) {
    }
}

package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class StubJiraServer implements AutoCloseable {

    private static final Pattern ISSUE = Pattern.compile("/rest/api/2/issue/([A-Z][A-Z0-9]*-\\d+)(/transitions|/remotelink)?");
    private static final Pattern AGILE_ISSUE = Pattern.compile("/rest/agile/1.0/issue/([A-Z][A-Z0-9]*-\\d+)");
    private static final Pattern SPRINT_ISSUES = Pattern.compile("/rest/agile/1.0/sprint/(\\d+)/issue");
    private static final List<String> WORKFLOW = FakeJira.WORKFLOW;

    private final HttpServer server;
    private final String token;
    private final JsonMapper json = new JsonMapper();
    private final List<Exchange> exchanges = new ArrayList<>();
    private final Map<String, StubIssue> issues = new LinkedHashMap<>();
    private final Map<Long, String> activeSprints = new LinkedHashMap<>();
    private Answer forced;
    private int lastNumber = 100;

    private StubJiraServer(String token) throws IOException {
        this.token = token;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::answered);
        this.server.start();
        this.activeSprints.put(7L, "Sprint 7");
    }

    public static StubJiraServer acceptingToken(String token) throws IOException {
        return new StubJiraServer(token);
    }

    public URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public synchronized StubJiraServer holding(String key, String summary, String status) {
        issues.put(key, new StubIssue(key, summary, status, null));
        return this;
    }

    public synchronized StubJiraServer withActiveSprints(Map<Long, String> sprints) {
        activeSprints.clear();
        activeSprints.putAll(sprints);
        return this;
    }

    public synchronized void answersNextWith(int status, String body) {
        forced = new Answer(status, body);
    }

    public synchronized List<Exchange> exchanges() {
        return List.copyOf(exchanges);
    }

    public synchronized String statusOf(String key) {
        return issues.get(key).status();
    }

    public synchronized String sprintOf(String key) {
        return issues.get(key).sprint();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answered(HttpExchange exchange) throws IOException {
        Answer answer;
        synchronized (this) {
            Exchange received = receivedFrom(exchange);
            exchanges.add(received);
            answer = answerTo(received);
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Exchange receivedFrom(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new Exchange(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization"), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Answer answerTo(Exchange received) {
        if (forced != null) {
            Answer once = forced;
            forced = null;
            return once;
        }
        if (!("Bearer " + token).equals(received.authorization())) {
            return new Answer(401, "");
        }
        return routed(received);
    }

    private Answer routed(Exchange received) {
        String route = received.method() + " " + received.path();
        Matcher issue = ISSUE.matcher(received.path());
        Matcher agileIssue = AGILE_ISSUE.matcher(received.path());
        Matcher sprintIssues = SPRINT_ISSUES.matcher(received.path());
        if (route.equals("GET /rest/api/2/search")) {
            return found(received.query());
        }
        if (route.equals("POST /rest/api/2/issue")) {
            return created(json.readTree(received.body()));
        }
        if (issue.matches()) {
            return onIssue(received, issue.group(1), issue.group(2));
        }
        if (agileIssue.matches() && received.method().equals("GET")) {
            return sprintOfIssue(agileIssue.group(1));
        }
        if (route.equals("GET /rest/agile/1.0/board")) {
            return ok(json.createObjectNode().set("values", json.createArrayNode().add(json.createObjectNode().put("id", 3))));
        }
        if (route.equals("GET /rest/agile/1.0/board/3/sprint")) {
            return activeSprints();
        }
        if (sprintIssues.matches() && received.method().equals("POST")) {
            return placedInSprint(Long.parseLong(sprintIssues.group(1)), json.readTree(received.body()));
        }
        return new Answer(404, "{\"errorMessages\":[\"No route " + route + "\"]}");
    }

    private Answer found(String query) {
        String project = query.replaceAll(".*project = \"([A-Z0-9]+)\".*", "$1");
        ArrayNode found = json.createArrayNode();
        issues.values().stream()
                .filter(issue -> issue.key().startsWith(project + "-") && !issue.status().equals("Done"))
                .limit(3)
                .forEach(issue -> found.add(issueNode(issue)));
        return ok(json.createObjectNode().set("issues", found));
    }

    private Answer created(JsonNode body) {
        String key = body.path("fields").path("project").path("key").asString() + "-" + ++lastNumber;
        issues.put(key, new StubIssue(key, body.path("fields").path("summary").asString(), WORKFLOW.getFirst(), null));
        return new Answer(201, json.createObjectNode().put("key", key).toString());
    }

    private Answer onIssue(Exchange received, String key, String part) {
        StubIssue issue = issues.get(key);
        if (issue == null) {
            return new Answer(404, "{\"errorMessages\":[\"Issue Does Not Exist\"]}");
        }
        if (part == null) {
            return received.method().equals("GET") ? ok(issueNode(issue)) : new Answer(204, "");
        }
        if (part.equals("/remotelink")) {
            return new Answer(201, "{\"id\":1}");
        }
        return received.method().equals("GET") ? transitionsOf(issue) : transitioned(issue, json.readTree(received.body()));
    }

    private Answer transitionsOf(StubIssue issue) {
        ArrayNode transitions = json.createArrayNode();
        int position = WORKFLOW.indexOf(issue.status());
        if (position + 1 < WORKFLOW.size()) {
            String next = WORKFLOW.get(position + 1);
            ObjectNode transition = json.createObjectNode().put("id", String.valueOf(position + 1)).put("name", "To " + next);
            transition.putObject("to").put("name", next);
            transitions.add(transition);
        }
        return ok(json.createObjectNode().set("transitions", transitions));
    }

    private Answer transitioned(StubIssue issue, JsonNode body) {
        String next = WORKFLOW.get(Integer.parseInt(body.path("transition").path("id").asString()));
        issues.put(issue.key(), new StubIssue(issue.key(), issue.summary(), next, issue.sprint()));
        return new Answer(204, "");
    }

    private Answer sprintOfIssue(String key) {
        StubIssue issue = issues.get(key);
        ObjectNode fields = json.createObjectNode();
        if (issue.sprint() == null) {
            fields.putNull("sprint");
        } else {
            fields.putObject("sprint").put("name", issue.sprint());
        }
        return ok(json.createObjectNode().put("key", key).set("fields", fields));
    }

    private Answer activeSprints() {
        ArrayNode sprints = json.createArrayNode();
        activeSprints.forEach((id, name) -> sprints.add(json.createObjectNode().put("id", id).put("name", name).put("state", "active")));
        return ok(json.createObjectNode().set("values", sprints));
    }

    private Answer placedInSprint(long sprint, JsonNode body) {
        body.path("issues").valueStream().map(JsonNode::asString).forEach(key -> {
            StubIssue issue = issues.get(key);
            issues.put(key, new StubIssue(key, issue.summary(), issue.status(), activeSprints.get(sprint)));
        });
        return new Answer(204, "");
    }

    private ObjectNode issueNode(StubIssue issue) {
        ObjectNode node = json.createObjectNode().put("key", issue.key());
        ObjectNode fields = node.putObject("fields").put("summary", issue.summary());
        fields.putObject("status").put("name", issue.status());
        return node;
    }

    private static Answer ok(JsonNode body) {
        return new Answer(200, body.toString());
    }

    public record Exchange(String method, String path, String query, String authorization, String body) {
    }

    private record Answer(int status, String body) {
    }

    private record StubIssue(String key, String summary, String status, String sprint) {
    }
}

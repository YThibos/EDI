package io.github.ythibos.edi.adapter.tracker;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.domain.Refusal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JiraCalls implements AutoCloseable {

    private static final Duration CONNECTING = Duration.ofSeconds(10);
    private static final Duration ANSWERING = Duration.ofSeconds(30);
    private static final String HIDDEN = "[secret]";

    private final HttpClient http;
    private final JsonMapper json;

    public JiraCalls(JsonMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder().proxy(EnvironmentProxies.of(System.getenv())).connectTimeout(CONNECTING).build();
    }

    JsonNode read(TrackerSettings settings, String path, Map<String, String> query, String what) {
        return exchange(settings, HttpRequest.newBuilder(addressOf(settings, path + queryOf(query))).GET(), what);
    }

    JsonNode write(TrackerSettings settings, String method, String path, JsonNode body, String what) {
        HttpRequest.Builder request = HttpRequest.newBuilder(addressOf(settings, path))
                .header("Content-Type", "application/json")
                .method(method, BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
        return exchange(settings, request, what);
    }

    @Override
    public void close() {
        http.close();
    }

    private JsonNode exchange(TrackerSettings settings, HttpRequest.Builder request, String what) {
        String token = KeysFile.tokenIn(settings.credentials());
        HttpResponse<String> response = sent(request.header("Authorization", "Bearer " + token).header("Accept", "application/json")
                .timeout(ANSWERING).build(), settings, what);
        return answerOf(response, settings, what, token);
    }

    private HttpResponse<String> sent(HttpRequest request, TrackerSettings settings, String what) {
        try {
            return http.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException unreachable) {
            throw new TrackerUnavailable("JIRA at " + settings.baseUrl().getHost() + " cannot be reached for " + what + ": "
                    + unreachable.getClass().getSimpleName() + ".");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TrackerUnavailable("The call to JIRA for " + what + " was interrupted.");
        }
    }

    private JsonNode answerOf(HttpResponse<String> response, TrackerSettings settings, String what, String token) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return bodyOf(response);
        }
        if (status == 401 || status == 403) {
            throw new Refusal("JIRA refused the access token for " + what + " (HTTP " + status + "); renew the token in the "
                    + settings.credentials().section() + " section of the keys file.");
        }
        if (status == 404) {
            throw new Refusal("JIRA found nothing for " + what + " (HTTP 404).");
        }
        String reasons = hidden(reasonsIn(response), token);
        if (status < 500) {
            throw new Refusal("JIRA refused " + what + " (HTTP " + status + ")" + reasons + ".");
        }
        throw new TrackerUnavailable("JIRA could not answer for " + what + " (HTTP " + status + ")" + reasons + ".");
    }

    private JsonNode bodyOf(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(body);
        } catch (JacksonException malformed) {
            throw new TrackerUnavailable("JIRA answered with something that is not JSON.");
        }
    }

    private String reasonsIn(HttpResponse<String> response) {
        try {
            JsonNode body = json.readTree(response.body());
            String reasons = Stream.concat(
                            body.path("errorMessages").valueStream().map(JsonNode::asString),
                            body.path("errors").propertyStream().map(field -> field.getKey() + ": " + field.getValue().asString()))
                    .filter(reason -> !reason.isBlank())
                    .collect(Collectors.joining("; "));
            return reasons.isEmpty() ? "" : ": " + reasons;
        } catch (JacksonException | IllegalArgumentException unreadable) {
            return "";
        }
    }

    private static String hidden(String text, String token) {
        return text.replace(token, HIDDEN);
    }

    private static URI addressOf(TrackerSettings settings, String path) {
        String base = settings.baseUrl().toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }

    private static String queryOf(Map<String, String> query) {
        return query.isEmpty() ? "" : query.entrySet().stream()
                .map(parameter -> parameter.getKey() + "=" + encoded(parameter.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

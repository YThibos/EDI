package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

final class CoreClient {

    private static final Duration PATIENCE = Duration.ofSeconds(5);
    private static final Duration ANSWER_PATIENCE = Duration.ofSeconds(60);
    private static final int OK = 200;
    private static final String JSON = "application/json";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(PATIENCE).build();

    CoreHealth health(CoreAddress core) {
        HttpRequest request = HttpRequest.newBuilder(core.endpoint("/api/health")).timeout(PATIENCE).GET().build();
        try {
            return healthFrom(http.send(request, BodyHandlers.discarding()).statusCode());
        } catch (IOException unreachable) {
            return CoreHealth.UNREACHABLE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CoreHealth.UNREACHABLE;
        }
    }

    CoreReply get(CoreConnection core, String path, String accepted) {
        return exchange(core, request(core, path, accepted, ANSWER_PATIENCE).GET());
    }

    CoreReply post(CoreConnection core, String path, String json, String accepted) {
        return post(core, path, json, accepted, ANSWER_PATIENCE);
    }

    CoreReply post(CoreConnection core, String path, String json, String accepted, Duration patience) {
        return exchange(core, request(core, path, accepted, patience).header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static CoreHealth healthFrom(int status) {
        return status == OK ? CoreHealth.UP : CoreHealth.DOWN;
    }

    private static HttpRequest.Builder request(CoreConnection core, String path, String accepted, Duration patience) {
        HttpRequest.Builder request = HttpRequest.newBuilder(core.address().endpoint(path))
                .timeout(patience)
                .header("Authorization", "Bearer " + core.token())
                .header("Accept", accepted);
        core.session().idIfAny().ifPresent(session -> request.header("EDI-Session", session));
        core.session().pidIfAny().ifPresent(pid -> request.header("EDI-Claude-Pid", pid));
        return request;
    }

    private CoreReply exchange(CoreConnection core, HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), BodyHandlers.ofString());
            return new CoreReply(response.statusCode(), response.body());
        } catch (IOException unreachable) {
            throw new CoreUnavailable("EDI core is not answering on " + core.address() + ".");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CoreUnavailable("The call to EDI core on " + core.address() + " was interrupted.");
        }
    }
}

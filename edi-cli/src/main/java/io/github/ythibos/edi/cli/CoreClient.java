package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

final class CoreClient {

    private static final Duration PATIENCE = Duration.ofSeconds(5);
    private static final int OK = 200;

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

    private static CoreHealth healthFrom(int status) {
        return status == OK ? CoreHealth.UP : CoreHealth.DOWN;
    }
}

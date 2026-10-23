package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

record TmuxPane(String socket, String pane) {

    private static final Duration PATIENCE = Duration.ofSeconds(2);
    private static final String WIDTH_FORMAT = "#{pane_width}";
    private static final int MOST_DIGITS = 5;

    static Optional<TmuxPane> ofEnvironment(Map<String, String> environment) {
        String server = environment.get("TMUX");
        String pane = environment.get("TMUX_PANE");
        if (isBlank(server) || isBlank(pane)) {
            return Optional.empty();
        }
        return Optional.of(new TmuxPane(socketOf(server), pane));
    }

    @SuppressWarnings("java:S4036") // tmux is found on the captain's own PATH, as the core finds git and gitleaks (docs/decisions/captain-report.md)
    OptionalInt width() {
        try {
            Process tmux = new ProcessBuilder("tmux", "-S", socket, "display-message", "-p", "-t", pane, WIDTH_FORMAT)
                    .redirectError(Redirect.DISCARD)
                    .start();
            tmux.getOutputStream().close();
            return widthAnsweredBy(tmux);
        } catch (IOException unavailable) {
            return OptionalInt.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String socketOf(String server) {
        return server.split(",", 2)[0];
    }

    private static OptionalInt widthAnsweredBy(Process tmux) throws IOException {
        try {
            if (!tmux.waitFor(PATIENCE.toMillis(), TimeUnit.MILLISECONDS) || tmux.exitValue() != 0) {
                tmux.destroyForcibly();
                return OptionalInt.empty();
            }
            try (InputStream answer = tmux.getInputStream()) {
                return columnsIn(new String(answer.readAllBytes(), StandardCharsets.UTF_8).strip());
            }
        } catch (InterruptedException interrupted) {
            tmux.destroyForcibly();
            Thread.currentThread().interrupt();
            return OptionalInt.empty();
        }
    }

    private static OptionalInt columnsIn(String answer) {
        boolean isColumnCount = !answer.isEmpty() && answer.length() <= MOST_DIGITS && answer.chars().allMatch(Character::isDigit);
        return isColumnCount ? OptionalInt.of(Integer.parseInt(answer)) : OptionalInt.empty();
    }
}

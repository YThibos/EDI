package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class StandInGitHub {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SERVER = "/backlog/stand-in-gh.mjs";

    private final Path bin;
    private final Path state;

    private StandInGitHub(Path bin, Path state) {
        this.bin = bin;
        this.state = state;
    }

    public static StandInGitHub serving(Path directory, String world) throws IOException {
        Path state = Files.createDirectories(directory.resolve("github-state"));
        Files.writeString(state.resolve("github.json"), world);
        Path bin = directory.resolve("bin");
        StandInScripts.installed(bin.resolve("gh"), "#!/bin/sh\nexec node '" + server() + "' \"$@\"\n");
        return new StandInGitHub(bin, state);
    }

    private static Path server() throws IOException {
        try {
            return Path.of(StandInGitHub.class.getResource(SERVER).toURI());
        } catch (URISyntaxException unreadable) {
            throw new IOException(SERVER + " has no file path.", unreadable);
        }
    }

    public Path bin() {
        return bin;
    }

    public JsonNode world() throws IOException {
        return JSON.readTree(Files.readString(state.resolve("github.json")));
    }

    public JsonNode issue(int number) throws IOException {
        for (JsonNode issue : world().get("issues")) {
            if (issue.get("number").asInt() == number) {
                return issue;
            }
        }
        throw new IllegalStateException("The stand-in holds no issue #" + number + ".");
    }

    public List<String> calls() throws IOException {
        Path calls = state.resolve("calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    public List<String> writes() throws IOException {
        return calls().stream().filter(call -> !call.contains("--method GET")).toList();
    }

    public Run run(TestProcesses processes, Path directory, Map<String, String> environment, List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        Path errors = Files.createTempFile(directory, "stderr", ".txt");
        builder.redirectError(errors.toFile());
        builder.environment().keySet().removeIf(variable -> variable.startsWith("GIT_CONFIG_") || variable.startsWith("GH_"));
        Map<String, String> isolated = new HashMap<>(environment);
        isolated.put("PATH", bin + ":" + builder.environment().get("PATH"));
        isolated.put("STATE", state.toString());
        builder.environment().putAll(isolated);
        Process process = processes.register(builder.start());
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(command + " did not finish.");
        }
        return new Run(process.exitValue(), output, Files.readString(errors));
    }

    public static List<String> node(Path script, String... arguments) {
        List<String> command = new ArrayList<>(List.of("node", script.toString()));
        command.addAll(List.of(arguments));
        return command;
    }

    public record Run(int exitCode, String output, String errors) {

        public String all() {
            return output + errors;
        }
    }
}

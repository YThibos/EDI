package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class FixtureRepository {

    private static final String DEFAULT_IDENTITY = "Fixture Author <fixture@example.org>";
    private static final String WITHOUT_BACKGROUND_MAINTENANCE = "maintenance.auto=false";

    private final Path root;

    private FixtureRepository(Path root) {
        this.root = root;
    }

    public static FixtureRepository at(Path root) {
        return new FixtureRepository(root);
    }

    public static FixtureRepository initialise(Path directory) throws IOException {
        FixtureRepository repository = new FixtureRepository(directory);
        repository.git("init", "--quiet", "--initial-branch=main");
        return repository.withoutBackgroundMaintenance();
    }

    public Path root() {
        return root;
    }

    public FixtureRepository write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    public FixtureRepository remove(String path) throws IOException {
        git("rm", "--quiet", path);
        return this;
    }

    public FixtureRepository commit(String message) throws IOException {
        return commitAs(DEFAULT_IDENTITY, message);
    }

    public FixtureRepository commitAs(String identity, String message) throws IOException {
        git("add", "--all");
        gitAs(identity, "commit", "--quiet", "--message", message);
        return this;
    }

    public FixtureRepository forceAdd(String path) throws IOException {
        git("add", "--force", path);
        return this;
    }

    public FixtureRepository branch(String name) throws IOException {
        git("switch", "--quiet", "--create", name);
        return this;
    }

    public FixtureRepository switchTo(String name) throws IOException {
        git("switch", "--quiet", name);
        return this;
    }

    public FixtureRepository clonedInto(Path directory) throws IOException {
        Files.createDirectories(directory.getParent());
        git("clone", "--quiet", root.toString(), directory.toString());
        return new FixtureRepository(directory).withoutBackgroundMaintenance();
    }

    public FixtureRepository configure(String key, String value) throws IOException {
        git("config", key, value);
        return this;
    }

    public String run(String... arguments) throws IOException {
        return git(arguments);
    }

    public String head() throws IOException {
        return git("rev-parse", "HEAD").strip();
    }

    private String git(String... arguments) throws IOException {
        return gitAs(DEFAULT_IDENTITY, arguments);
    }

    private String gitAs(String identity, String... arguments) throws IOException {
        ProcessBuilder git = new ProcessBuilder(Stream.concat(Stream.of("git", "-c", WITHOUT_BACKGROUND_MAINTENANCE), Stream.of(arguments)).toList())
                .directory(root.toFile())
                .redirectErrorStream(true);
        git.environment().keySet().removeIf(variable -> injectsConfiguration(variable) || namesAnEdiCaller(variable));
        git.environment().putAll(isolatedEnvironment(identity));
        git.environment().put("EDI_HOME", root.resolveSibling(root.getFileName() + ".hook-home").toString());
        Process process = git.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        awaitSuccess(process, arguments, output);
        return output;
    }

    private static boolean injectsConfiguration(String variable) {
        return variable.equals("GIT_CONFIG_COUNT") || variable.equals("GIT_CONFIG_PARAMETERS")
                || variable.startsWith("GIT_CONFIG_KEY_") || variable.startsWith("GIT_CONFIG_VALUE_");
    }

    private static boolean namesAnEdiCaller(String variable) {
        return variable.startsWith("EDI_") || variable.startsWith("CLAUDE_CODE_");
    }

    private static Map<String, String> isolatedEnvironment(String identity) {
        List<String> nameAndEmail = List.of(identity.substring(0, identity.indexOf(" <")),
                identity.substring(identity.indexOf('<') + 1, identity.indexOf('>')));
        return Map.of(
                "GIT_CONFIG_GLOBAL", "/dev/null",
                "GIT_CONFIG_NOSYSTEM", "1",
                "GIT_AUTHOR_NAME", nameAndEmail.get(0),
                "GIT_AUTHOR_EMAIL", nameAndEmail.get(1),
                "GIT_COMMITTER_NAME", nameAndEmail.get(0),
                "GIT_COMMITTER_EMAIL", nameAndEmail.get(1));
    }

    private static void awaitSuccess(Process process, String[] arguments, String output) throws IOException {
        try {
            if (!process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("git " + String.join(" ", arguments) + " did not finish");
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("git " + String.join(" ", arguments) + " was interrupted", interrupted);
        }
        if (process.exitValue() != 0) {
            throw new IOException("git " + String.join(" ", arguments) + " failed: " + output);
        }
    }

    private FixtureRepository withoutBackgroundMaintenance() throws IOException {
        return configure("maintenance.auto", "false");
    }
}

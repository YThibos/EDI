package io.github.ythibos.edi.adapter.gitleaks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.TextFinding;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

@Component
@RegisterReflectionForBinding(GitleaksFinding.class)
public class Gitleaks {

    private static final String STANDARD_RULES_ONLY = """
            [extend]
            useDefault = true
            """;

    private final ExternalPrograms programs;
    private final JsonMapper json;

    public Gitleaks(ExternalPrograms programs, JsonMapper json) {
        this.programs = programs;
        this.json = json;
    }

    public List<PublishFinding> secretsIn(Path repository, RevisionRange outgoing) {
        try (ScanWorkspace workspace = ScanWorkspace.create(STANDARD_RULES_ONLY)) {
            programs.run(Program.GITLEAKS, repository, scanArguments(outgoing, workspace)).outputOfSuccess();
            return Stream.of(json.readValue(workspace.report().toFile(), GitleaksFinding[].class))
                    .map(GitleaksFinding::asPublishFinding)
                    .toList();
        }
    }

    public List<TextFinding> secretsIn(RequestText text) {
        try (ScanWorkspace workspace = ScanWorkspace.create(STANDARD_RULES_ONLY)) {
            Path parts = workspace.holding(text);
            programs.run(Program.GITLEAKS, parts, textScanArguments(parts, workspace)).outputOfSuccess();
            return Stream.of(json.readValue(workspace.report().toFile(), GitleaksFinding[].class))
                    .map(GitleaksFinding::asTextFinding)
                    .toList();
        }
    }

    private static List<String> scanArguments(RevisionRange outgoing, ScanWorkspace workspace) {
        return List.of(
                "git",
                "--log-opts=" + String.join(" ", outgoing.revisions()),
                "--config=" + workspace.rules(),
                "--gitleaks-ignore-path=" + workspace.directory(),
                "--ignore-gitleaks-allow",
                "--redact",
                "--report-format=json",
                "--report-path=" + workspace.report(),
                "--exit-code=0",
                "--no-banner",
                "--no-color",
                "--log-level=error",
                ".");
    }

    private static List<String> textScanArguments(Path parts, ScanWorkspace workspace) {
        return List.of(
                "dir",
                "--config=" + workspace.rules(),
                "--redact",
                "--report-format=json",
                "--report-path=" + workspace.report(),
                "--exit-code=0",
                "--no-banner",
                "--no-color",
                "--log-level=error",
                parts.toString());
    }

    private record ScanWorkspace(Path directory) implements AutoCloseable {

        private static final String TEXT_PARTS = "text";
        private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

        static ScanWorkspace create(String rules) {
            try {
                ScanWorkspace workspace = new ScanWorkspace(Files.createTempDirectory("edi-gitleaks-", OWNER_ONLY));
                Files.writeString(workspace.rules(), rules);
                return workspace;
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }

        Path rules() {
            return directory.resolve("rules.toml");
        }

        Path report() {
            return directory.resolve("report.json");
        }

        Path holding(RequestText text) {
            try {
                Path parts = Files.createDirectory(directory.resolve(TEXT_PARTS));
                Files.writeString(parts.resolve(RequestText.TITLE), text.title());
                Files.writeString(parts.resolve(RequestText.DESCRIPTION), text.description());
                return parts;
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }

        @Override
        public void close() {
            try {
                Path parts = directory.resolve(TEXT_PARTS);
                Files.deleteIfExists(parts.resolve(RequestText.TITLE));
                Files.deleteIfExists(parts.resolve(RequestText.DESCRIPTION));
                Files.deleteIfExists(parts);
                Files.deleteIfExists(rules());
                Files.deleteIfExists(report());
                Files.deleteIfExists(directory);
            } catch (IOException undeletable) {
                throw new UncheckedIOException(undeletable);
            }
        }
    }
}

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

    private record ScanWorkspace(Path directory) implements AutoCloseable {

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

        @Override
        public void close() {
            try {
                Files.deleteIfExists(rules());
                Files.deleteIfExists(report());
                Files.deleteIfExists(directory);
            } catch (IOException undeletable) {
                throw new UncheckedIOException(undeletable);
            }
        }
    }
}

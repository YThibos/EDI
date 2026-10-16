package io.github.ythibos.edi.adapter.build;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;

@Component
public class ProjectBuild {

    private static final Duration PATIENCE = Duration.ofMinutes(60);
    private static final int SUCCESS = 0;

    private final ExternalPrograms programs;

    public ProjectBuild(ExternalPrograms programs) {
        this.programs = programs;
    }

    public boolean verifies(Path worktree, String verifyCommand, Path log) {
        return programs.runInto(log, Program.SHELL, worktree, List.of("-c", verifyCommand), PATIENCE) == SUCCESS;
    }
}

package io.github.ythibos.edi.application.statusline;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;

@Service
public class StatusLineQuery {

    private final Git git;

    public StatusLineQuery(Git git) {
        this.git = git;
    }

    public StatusLine lineFor(ClaudeSessionStatus session, Path workingDirectory) {
        return new StatusLine(session, git.checkedOutBranchAt(workingDirectory));
    }
}

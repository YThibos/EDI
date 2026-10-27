package io.github.ythibos.edi.application.statusline;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.application.primary.PrimaryWaitQuery;
import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;

@Service
public class StatusLineQuery {

    private final Git git;
    private final PrimaryWaitQuery waits;

    public StatusLineQuery(Git git, PrimaryWaitQuery waits) {
        this.git = git;
        this.waits = waits;
    }

    public StatusLine lineFor(ClaudeSessionStatus session, Path workingDirectory) {
        return new StatusLine(session, git.checkedOutBranchAt(workingDirectory), waits.current());
    }
}

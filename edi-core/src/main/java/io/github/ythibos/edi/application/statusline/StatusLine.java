package io.github.ythibos.edi.application.statusline;

import java.util.Optional;

import io.github.ythibos.edi.domain.branch.CheckedOutBranch;
import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;

public record StatusLine(ClaudeSessionStatus session, Optional<CheckedOutBranch> branch, PrimaryWait primaryWait) {
}

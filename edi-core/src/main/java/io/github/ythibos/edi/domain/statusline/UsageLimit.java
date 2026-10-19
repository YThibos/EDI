package io.github.ythibos.edi.domain.statusline;

import java.time.Instant;
import java.util.Optional;

public record UsageLimit(int usedPercentage, Optional<Instant> resetsAt) {
}

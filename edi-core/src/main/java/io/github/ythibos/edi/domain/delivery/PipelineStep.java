package io.github.ythibos.edi.domain.delivery;

import java.util.Locale;

public enum PipelineStep {
    REBASE,
    CI;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}

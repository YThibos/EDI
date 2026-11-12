package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public class InvalidConfiguration extends Refusal {

    private static final long serialVersionUID = 1L;

    InvalidConfiguration(Path file, String reason) {
        super(Rule.CONFIGURATION_VALID, file + " is refused: " + reason);
    }
}

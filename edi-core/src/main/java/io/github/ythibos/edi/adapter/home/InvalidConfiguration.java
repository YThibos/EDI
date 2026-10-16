package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.Refusal;

public class InvalidConfiguration extends Refusal {

    private static final long serialVersionUID = 1L;

    InvalidConfiguration(Path file, String reason) {
        super(file + " is refused: " + reason);
    }
}

package io.github.ythibos.edi.domain.board;

import java.util.Objects;

public record BoardAttachment(String path, String mime, String name) {

    public BoardAttachment {
        Objects.requireNonNull(path, "An attachment the captain added to a board is a file with a path.");
        mime = Objects.requireNonNullElse(mime, "");
        name = Objects.requireNonNullElse(name, "");
    }
}

package io.github.ythibos.edi.domain.board;

public record BoardId(long value) {

    public BoardId {
        if (value < 1) {
            throw new IllegalArgumentException("Boards are numbered from 1: " + value);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

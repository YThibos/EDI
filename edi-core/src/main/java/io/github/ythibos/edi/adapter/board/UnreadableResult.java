package io.github.ythibos.edi.adapter.board;

class UnreadableResult extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnreadableResult(String reason) {
        super(reason);
    }
}

package io.github.ythibos.edi.domain.guard;

public class UnreadableCommand extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnreadableCommand(String reason) {
        super(reason);
    }
}

package io.github.ythibos.edi.adapter.process;

public class ProgramFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProgramFailure(String message) {
        super(message);
    }

    public ProgramFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

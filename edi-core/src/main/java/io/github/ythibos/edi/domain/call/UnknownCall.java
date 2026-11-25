package io.github.ythibos.edi.domain.call;

public class UnknownCall extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownCall(CallKey call) {
        super("Nothing on the captain's list is " + call + ".");
    }
}

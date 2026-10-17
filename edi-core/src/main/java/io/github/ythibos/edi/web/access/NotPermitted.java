package io.github.ythibos.edi.web.access;

public class NotPermitted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotPermitted(String reason) {
        super(reason);
    }
}

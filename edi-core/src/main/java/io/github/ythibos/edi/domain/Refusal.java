package io.github.ythibos.edi.domain;

public class Refusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Refusal(String reason) {
        super(reason);
    }
}

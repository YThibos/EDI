package io.github.ythibos.edi.domain.spark;

public class UnknownSpark extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownSpark(int number) {
        super("No spark is numbered " + number + "; edi spark list shows the open ones.");
    }
}

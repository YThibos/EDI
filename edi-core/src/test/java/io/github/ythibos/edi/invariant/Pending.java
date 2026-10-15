package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.Assumptions;

final class Pending {

    private Pending() {
    }

    static void until(Slice slice) {
        Assumptions.abort("pending until slice " + slice);
    }
}

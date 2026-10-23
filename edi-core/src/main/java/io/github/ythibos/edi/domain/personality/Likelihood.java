package io.github.ythibos.edi.domain.personality;

public enum Likelihood {

    NEVER,
    RARELY,
    SOMETIMES,
    USUALLY,
    ALMOST_ALWAYS;

    private static final double ALMOST_ALWAYS_FROM = 0.8;
    private static final double USUALLY_FROM = 0.5;
    private static final double SOMETIMES_FROM = 0.25;

    static Likelihood of(double chance) {
        if (chance >= ALMOST_ALWAYS_FROM) {
            return ALMOST_ALWAYS;
        }
        if (chance >= USUALLY_FROM) {
            return USUALLY;
        }
        if (chance >= SOMETIMES_FROM) {
            return SOMETIMES;
        }
        return chance > 0 ? RARELY : NEVER;
    }
}

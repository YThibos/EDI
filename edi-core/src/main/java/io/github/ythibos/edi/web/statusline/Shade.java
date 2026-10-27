package io.github.ythibos.edi.web.statusline;

final class Shade {

    static final TerminalColour DARK_AMBER = new TerminalColour(160, 120, 40);
    static final TerminalColour DARK_RED = new TerminalColour(160, 40, 40);

    private static final TerminalColour DARK_GREEN = new TerminalColour(40, 130, 40);
    private static final int NONE = 0;
    private static final int HALF = 50;
    private static final int ALL = 100;

    private Shade() {
    }

    static TerminalColour of(int percentage) {
        int share = Math.clamp(percentage, NONE, ALL);
        return share <= HALF ? DARK_GREEN.towards(DARK_AMBER, share, HALF) : DARK_AMBER.towards(DARK_RED, share - HALF, HALF);
    }

    static TerminalColour theOtherWay(int percentage) {
        return of(ALL - percentage);
    }
}

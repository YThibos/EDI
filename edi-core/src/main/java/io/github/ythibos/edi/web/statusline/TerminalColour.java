package io.github.ythibos.edi.web.statusline;

record TerminalColour(int red, int green, int blue) {

    private static final String ESCAPE = "\u001b[";
    private static final String PLAIN = ESCAPE + "0m";

    String paint(String text) {
        return ESCAPE + "38;2;" + red + ";" + green + ";" + blue + "m" + text + PLAIN;
    }

    TerminalColour towards(TerminalColour end, int step, int steps) {
        return new TerminalColour(between(red, end.red, step, steps), between(green, end.green, step, steps), between(blue, end.blue, step, steps));
    }

    private static int between(int start, int end, int step, int steps) {
        return start + (end - start) * step / steps;
    }
}

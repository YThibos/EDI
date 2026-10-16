package io.github.ythibos.edi.domain.worker;

public sealed interface Keystroke {

    static Keystroke line(String text) {
        return new Line(text);
    }

    static Keystroke escape() {
        return new Escape();
    }

    record Line(String text) implements Keystroke {

        private static final int LONGEST = 400;

        public Line {
            if (text == null || text.isBlank() || text.length() > LONGEST) {
                throw new IllegalArgumentException("A line typed into a worker is one short line of text.");
            }
            if (text.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("A line typed into a worker is literal text; it carries no control character or key.");
            }
        }
    }

    record Escape() implements Keystroke {
    }
}

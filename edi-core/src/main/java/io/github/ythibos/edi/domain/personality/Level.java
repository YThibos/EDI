package io.github.ythibos.edi.domain.personality;

public record Level(int value) {

    private static final int LOWEST_VALUE = 0;
    private static final int HIGHEST_VALUE = 10;

    public static final Level LOWEST = new Level(LOWEST_VALUE);

    public Level {
        if (!admits(value)) {
            throw new IllegalArgumentException("A personality level runs from 0 to 10, not " + value + ".");
        }
    }

    public static boolean admits(int value) {
        return value >= LOWEST_VALUE && value <= HIGHEST_VALUE;
    }

    public static Level of(int value) {
        return new Level(value);
    }

    static Level parse(String written) {
        try {
            return of(Integer.parseInt(written.strip()));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("A personality level is a whole number from 0 to 10, not " + written + ".");
        }
    }

    Level atMost(Level ceiling) {
        return value > ceiling.value ? ceiling : this;
    }

    Level atLeast(Level floor) {
        return value < floor.value ? floor : this;
    }

    double shareOfTheHighest() {
        return (double) value / HIGHEST_VALUE;
    }
}

package io.github.ythibos.edi.domain.presence;

public record Mandate(String words, int workerCap) {

    public Mandate {
        words = words.strip();
        if (words.isEmpty()) {
            throw new IllegalArgumentException("Away mode carries the captain's own words as its mandate.");
        }
        if (workerCap < 0) {
            throw new IllegalArgumentException("Away mode caps the workers at the number running when the captain left, never below none.");
        }
    }
}

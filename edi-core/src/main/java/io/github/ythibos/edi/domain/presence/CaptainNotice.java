package io.github.ythibos.edi.domain.presence;

public record CaptainNotice(String title, String text) {

    public CaptainNotice {
        if (title.isBlank() || text.isBlank()) {
            throw new IllegalArgumentException("A notification for the captain has a title and a sentence.");
        }
    }
}

package io.github.ythibos.edi.web.board;

import java.nio.file.Path;

record PostRequest(String file, String link, Integer questions) {

    Path board() {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Posting a board round names the board's HTML file.");
        }
        return Path.of(file);
    }

    int questionsAsked() {
        if (questions == null) {
            throw new IllegalArgumentException("Posting a board round counts the questions it puts before the captain.");
        }
        return questions;
    }
}

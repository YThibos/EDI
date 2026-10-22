package io.github.ythibos.edi.domain.board;

import java.util.List;
import java.util.Locale;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;

public record BoardRound(String link, int questions, List<DecisionNumber> bound) {

    private static final List<String> WEB_SCHEMES = List.of("http://", "https://");

    public BoardRound {
        link = link == null ? "" : link.strip();
        bound = List.copyOf(bound);
        if (WEB_SCHEMES.stream().noneMatch(link.toLowerCase(Locale.ROOT)::startsWith) || link.contains(" ")) {
            throw new Refusal("A board round names the web link the captain opens the board at, such as http://127.0.0.1:4387/session/1a2b; "
                    + "\"" + link + "\" is not one.");
        }
        if (questions < 0) {
            throw new Refusal("A board round counts its questions from 0, not " + questions + ".");
        }
        if (questions == 0 && bound.isEmpty()) {
            throw new Refusal("A board round puts a question or an open decision before the captain; this one counts no question and binds no decision.");
        }
    }
}

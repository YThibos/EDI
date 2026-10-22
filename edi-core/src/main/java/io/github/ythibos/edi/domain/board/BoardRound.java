package io.github.ythibos.edi.domain.board;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;

public record BoardRound(String link, int questions, List<BoardBinding> bindings, BoardCard card) {

    private static final List<String> WEB_SCHEMES = List.of("http://", "https://");

    public BoardRound {
        link = link == null ? "" : link.strip();
        bindings = List.copyOf(bindings);
        if (WEB_SCHEMES.stream().noneMatch(link.toLowerCase(Locale.ROOT)::startsWith) || link.contains(" ")) {
            throw new Refusal("A board round names the web link the captain opens the board at, such as http://127.0.0.1:4387/session/1a2b; "
                    + "\"" + link + "\" is not one.");
        }
        if (questions < 0) {
            throw new Refusal("A board round counts its questions from 0, not " + questions + ".");
        }
        if (questions == 0 && bindings.isEmpty() && card == null) {
            throw new Refusal("A board round puts a question or a decision before the captain; this one counts no question, binds no decision and "
                    + "carries no card.");
        }
    }

    public BoardRound(String link, int questions, List<BoardBinding> bindings) {
        this(link, questions, bindings, null);
    }

    public List<DecisionNumber> bound() {
        return bindings.stream().map(BoardBinding::decision).toList();
    }

    public Optional<BoardCard> carriedCard() {
        return Optional.ofNullable(card);
    }

    public BoardRound withItsCardRaisedAs(DecisionNumber decision) {
        Stream<BoardBinding> others = bindings.stream().filter(binding -> !binding.decision().equals(decision));
        return new BoardRound(link, questions, Stream.concat(others, Stream.of(card.raisedAs(decision))).toList());
    }

    public BoardRound carryingForward(List<BoardBinding> earlier) {
        Stream<BoardBinding> bound = bindings.stream().map(binding -> withTheQuestionKeptFrom(earlier, binding));
        Stream<BoardBinding> carried = earlier.stream().filter(binding -> !bound().contains(binding.decision()));
        return new BoardRound(link, questions, Stream.concat(bound, carried).toList(), card);
    }

    private static BoardBinding withTheQuestionKeptFrom(List<BoardBinding> earlier, BoardBinding binding) {
        if (!binding.question().isEmpty()) {
            return binding;
        }
        return earlier.stream().filter(kept -> kept.decision().equals(binding.decision())).findFirst().orElse(binding);
    }
}

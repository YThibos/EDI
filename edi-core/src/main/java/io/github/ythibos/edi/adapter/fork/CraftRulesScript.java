package io.github.ythibos.edi.adapter.fork;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.IntStream;

final class CraftRulesScript {

    private static final String PRINTING_FUNCTION = "print_rules()";
    private static final String HERE_DOCUMENT = "<<'EOF'";
    private static final String HERE_DOCUMENT_END = "EOF";

    private CraftRulesScript() {
    }

    static Optional<String> printedRules(String script) {
        List<String> lines = script.lines().toList();
        OptionalInt function = firstFrom(lines, 0, line -> line.startsWith(PRINTING_FUNCTION));
        OptionalInt opening = after(lines, function, line -> line.strip().endsWith(HERE_DOCUMENT));
        OptionalInt closing = after(lines, opening, line -> line.equals(HERE_DOCUMENT_END));
        if (closing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("\n", lines.subList(opening.getAsInt() + 1, closing.getAsInt())) + "\n");
    }

    private static OptionalInt after(List<String> lines, OptionalInt previous, Predicate<String> matches) {
        return previous.isEmpty() ? OptionalInt.empty() : firstFrom(lines, previous.getAsInt() + 1, matches);
    }

    private static OptionalInt firstFrom(List<String> lines, int start, Predicate<String> matches) {
        return IntStream.range(start, lines.size()).filter(index -> matches.test(lines.get(index))).findFirst();
    }
}

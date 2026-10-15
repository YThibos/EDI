package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record DenyList(List<DeniedTerm> terms) {

    private static final String COMMENT = "#";

    public DenyList {
        terms = List.copyOf(terms);
    }

    public static DenyList empty() {
        return new DenyList(List.of());
    }

    public static DenyList parse(String entries) {
        List<String> lines = entries.lines().map(String::strip).toList();
        return new DenyList(IntStream.range(0, lines.size())
                .filter(index -> declaresATerm(lines.get(index)))
                .mapToObj(index -> new DeniedTerm(index + 1, lines.get(index)))
                .toList());
    }

    public List<PublishFinding> findIn(OutgoingChanges changes) {
        return changes.commits().stream().flatMap(this::findIn).toList();
    }

    private static boolean declaresATerm(String line) {
        return !line.isEmpty() && !line.startsWith(COMMENT);
    }

    private Stream<PublishFinding> findIn(OutgoingCommit commit) {
        return Stream.of(
                        inCommitPart("author", commit.author(), commit),
                        inCommitPart("committer", commit.committer(), commit),
                        inCommitPart("message", commit.message(), commit),
                        commit.addedLines().stream().flatMap(line -> onAddedLine(line, commit)))
                .flatMap(Function.identity());
    }

    private Stream<PublishFinding> inCommitPart(String part, String text, OutgoingCommit commit) {
        return termsIn(text).map(term ->
                PublishFinding.onCommit(PublishRule.DENIED_TERM, part, commit.id(), reasonFor(term)));
    }

    private Stream<PublishFinding> onAddedLine(AddedLine line, OutgoingCommit commit) {
        return termsIn(line.text()).map(term ->
                PublishFinding.onLine(PublishRule.DENIED_TERM, line.path(), line.number(), commit.id(), reasonFor(term)));
    }

    private Stream<DeniedTerm> termsIn(String text) {
        return terms.stream().filter(term -> term.appearsIn(text));
    }

    private static String reasonFor(DeniedTerm term) {
        return "deny-list entry " + term.entry();
    }
}

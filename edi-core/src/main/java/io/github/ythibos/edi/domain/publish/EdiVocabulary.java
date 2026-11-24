package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class EdiVocabulary {

    private static final Pattern ADDRESS_WORD = Pattern.compile("(?<!\\p{Alnum})(?:captain|commander)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_CODE = Pattern.compile(
            "(?<![\\w:./-])(?:(?:D\\d++|Q\\d++(?! \\d{4}(?!\\d)))(?: [A-Z](?!\\w))?|[A-Z]+\\d+ [A-Z])(?![\\w:/-]|[.,]\\w)");

    private EdiVocabulary() {
    }

    public static List<PublishFinding> findIn(OutgoingChanges changes, BiFunction<String, String, String> contentAtCommit) {
        return changes.commits().stream()
                .flatMap(commit -> findIn(commit, path -> contentAtCommit.apply(commit.id(), path)))
                .toList();
    }

    public static List<TextFinding> findInLines(String part, String text) {
        List<String> lines = text.lines().toList();
        return IntStream.range(0, lines.size())
                .boxed()
                .flatMap(index -> findingsAt(RequestText.placeOf(part, index + 1), lines.get(index)))
                .toList();
    }

    public static List<TextFinding> findIn(String place, String text) {
        return findingsAt(place, text).toList();
    }

    static Stream<TextFinding> findingsOn(RequestText.TextLine line) {
        return findingsAt(line.place(), line.text());
    }

    private static Stream<TextFinding> findingsAt(String place, String text) {
        return reasonsIn(text).map(reason -> new TextFinding(PublishRule.EDI_VOCABULARY, place, reason));
    }

    private static Stream<PublishFinding> findIn(OutgoingCommit commit, Function<String, String> contentAt) {
        List<String> publishedDocuments = commit.paths().stream().filter(path -> PublishedDocument.isOne(path, contentAt)).toList();
        return Stream.concat(
                reasonsIn(commit.message()).map(reason -> PublishFinding.onCommit(PublishRule.EDI_VOCABULARY, "message", commit.id(), reason)),
                commit.addedLines().stream()
                        .filter(line -> publishedDocuments.contains(line.path()))
                        .flatMap(line -> reasonsIn(line.text()).map(reason ->
                                PublishFinding.onLine(PublishRule.EDI_VOCABULARY, line.path(), line.number(), commit.id(), reason))));
    }

    private static Stream<String> reasonsIn(String text) {
        return Stream.concat(
                matches(ADDRESS_WORD, text).map(EdiVocabulary::addressWordReason),
                matches(INTERNAL_CODE, text).map(EdiVocabulary::internalCodeReason));
    }

    private static Stream<String> matches(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(MatchResult::group);
    }

    private static String addressWordReason(String word) {
        return "\"" + word + "\" is internal vocabulary; write \"developer\" instead";
    }

    private static String internalCodeReason(String code) {
        return "\"" + code + "\" is an internal decision or question code; leave it out and say the question and its answer in plain words";
    }
}

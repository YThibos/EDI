package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record TextVerdict(List<TextFinding> findings) {

    public TextVerdict {
        findings = List.copyOf(findings);
    }

    public static TextVerdict of(RequestText text, DenyList denyList, List<TextFinding> secrets) {
        return new TextVerdict(Stream.concat(secrets.stream(), text.lines().stream().flatMap(line -> findingsOn(line, denyList))).toList());
    }

    public boolean allowsPosting() {
        return findings.isEmpty();
    }

    public String refusal() {
        return "The publish check refuses the request's text: " + places() + "; rewrite it without what those places name,"
                + " since a request's text is published as a commit is.";
    }

    public String asAGapOf(String request) {
        return "the publish check refuses the text of " + request + " (" + places() + "), so rewrite it on the forge without what those places name";
    }

    private String places() {
        return String.join("; ", findings.stream().map(TextFinding::toString).toList());
    }

    private static Stream<TextFinding> findingsOn(RequestText.TextLine line, DenyList denyList) {
        return Stream.of(
                        denyList.termsIn(line.text()).map(term -> new TextFinding(PublishRule.DENIED_TERM, line.place(), "deny-list entry " + term.entry())),
                        HomePaths.appearIn(line.text())
                                ? Stream.of(new TextFinding(PublishRule.PERSONAL_PATH, line.place(), "a home path"))
                                : Stream.<TextFinding>empty(),
                        NetworkAddresses.foundIn(line.text()).stream().map(what -> new TextFinding(PublishRule.NETWORK_ADDRESS, line.place(), what)))
                .flatMap(findings -> findings);
    }
}

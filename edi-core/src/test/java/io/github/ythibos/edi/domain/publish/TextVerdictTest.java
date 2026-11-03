package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextVerdictTest {

    private static final DenyList DENY_LIST = DenyList.parse("# synthetic deny-list\njdoe-at-acme\nacme-corp.example\n");

    @Test
    void namesEachPlaceAndRuleWithoutTheValueItFound() {
        RequestText text = new RequestText("HRN-3 Fit the lamp for jdoe-at-acme", """
                Fits the brass lamp.
                Built on build.acme-corp.example from /home/jdoe/projects/harness.
                The proxy at 10.20.30.40 answered.
                """);

        TextVerdict verdict = TextVerdict.of(text, DENY_LIST, List.of(new TextFinding(PublishRule.SECRET, "line 1 of the description", "gitleaks rule x")));

        assertThat(verdict.allowsPosting()).isFalse();
        assertThat(verdict.findings()).extracting(TextFinding::rule, TextFinding::place, TextFinding::reason).containsExactly(
                tuple(PublishRule.SECRET, "line 1 of the description", "gitleaks rule x"),
                tuple(PublishRule.DENIED_TERM, "the title", "deny-list entry 2"),
                tuple(PublishRule.DENIED_TERM, "line 2 of the description", "deny-list entry 3"),
                tuple(PublishRule.PERSONAL_PATH, "line 2 of the description", "a home path"),
                tuple(PublishRule.NETWORK_ADDRESS, "line 3 of the description", "an IP address outside the documentation ranges"));
        assertThat(verdict.refusal()).startsWith("The publish check refuses the request's text: line 1 of the description: gitleaks rule x; the title:")
                .doesNotContain("jdoe", "acme", "10.20.30.40");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "The proxy at 10.20.30.40 answered.",
        "Bound to 172.16.0.5 and 192.168.1.20.",
        "The core listens on 127.0.0.1.",
        "Resolved to 8.8.8.8 at last.",
        "Reached fe80::1 on the link.",
        "Reached fd12:3456:789a::1 inside.",
        "The proxy at proxy.internal:3128 answered.",
        "Open localhost:5490 in the browser.",
        "Fetched http://build.example.org:8080/job/ in the end.",
        "Signed in to git.corp:22."})
    void refusesAnAddressOrAHostWithItsPort(String line) {
        assertThat(TextVerdict.of(new RequestText("HRN-3 Fit the lamp", line), DenyList.empty(), List.of()).findings())
                .extracting(TextFinding::rule).containsExactly(PublishRule.NETWORK_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Ran from /home/jdoe/projects/harness.",
        "Ran from /Users/jdoe/projects.",
        "Ran from /root/projects.",
        "Ran from /mnt/c/Users/jdoe/Downloads.",
        "Ran from C:\\Users\\jdoe\\Downloads."})
    void refusesAHomePath(String line) {
        assertThat(TextVerdict.of(new RequestText("HRN-3 Fit the lamp", line), DenyList.empty(), List.of()).findings())
                .extracting(TextFinding::rule).containsExactly(PublishRule.PERSONAL_PATH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "The documentation proxy at 192.0.2.10 answered, and so did 198.51.100.7, 203.0.113.9 and 2001:db8::1.",
        "Verified on Claude Code 2.1.312 and GraalVM 25.4.4.1.1.",
        "See edi-core/src/test/java/io/github/ythibos/edi/web/ApiTest.java:192 and docs/decisions/publish-guard.md:12.",
        "The run started at 09:30 and ended at 14:05:59.",
        "It uses String::strip and Map::entry.",
        "Opened https://github.com/example-owner/harness/pull/42 for review.",
        "Ran from ~/.edi/state, the EDI home.",
        "D1 Which lamp? » Recommended: A"})
    void letsAnOrdinaryDescriptionThrough(String line) {
        assertThat(TextVerdict.of(new RequestText("HRN-3 Fit the lamp", line), DENY_LIST, List.of()).allowsPosting()).as(line).isTrue();
    }
}

package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EdiVocabularyTest {

    private static final String STORY = """
            ---
            key: HRN-3
            type: Story
            ---
            """;

    @ParameterizedTest
    @ValueSource(strings = {
        "The captain's word of 9 November.",
        "As the Captain asked.",
        "Approved by the COMMANDER.",
        "Both captains agreed.",
        "Marked captain-approved."})
    void refusesTheWordsForThePersonEdiWorksFor(String line) {
        assertThat(EdiVocabulary.findIn("the summary", line)).extracting(TextFinding::rule, TextFinding::place)
                .containsExactly(tuple(PublishRule.EDI_VOCABULARY, "the summary"));
        assertThat(EdiVocabulary.findIn("the summary", line).getFirst().reason()).endsWith("is internal vocabulary; write \"developer\" instead");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Settled by D103.",
        "The developer chose D139 B on 19/11/2026.",
        "Auditing only the decision's entries (Q4 B) was rejected.",
        "Answered Q10 A, then Q15 A.",
        "The plan's PLAN2 A holds.",
        "D139's option text said otherwise."})
    void refusesTheDecisionAndQuestionCodes(String line) {
        assertThat(EdiVocabulary.findIn("the summary", line)).isNotEmpty().allSatisfy(finding ->
                assertThat(finding.reason()).endsWith("is an internal decision or question code; leave it out and say the question and its answer in plain words"));
    }

    @Test
    void namesTheCodeWithItsAnswer() {
        assertThat(EdiVocabulary.findIn("the summary", "The developer chose D139 B and then Q4 B.")).extracting(TextFinding::reason)
                .allSatisfy(reason -> assertThat(reason).startsWith("\""))
                .anySatisfy(reason -> assertThat(reason).startsWith("\"D139 B\""))
                .anySatisfy(reason -> assertThat(reason).startsWith("\"Q4 B\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "The developer's word of 9 November.",
        "Parse the arguments with jcommander.",
        "The fingerprint ends 65:53:D3 as expected.",
        "Pick FED3 for the person.",
        "Booked on JUSTMD-75 and EDI-237.",
        "Due in Q1 2027, reviewed in Q4.",
        "Drawn with D3.js on an A4 page.",
        "Times follow ISO 8601 and Java 25."})
    void letsPlainProjectWordingThrough(String line) {
        assertThat(EdiVocabulary.findIn("the summary", line)).isEmpty();
    }

    @Test
    void namesEachLineOfAText() {
        assertThat(EdiVocabulary.findInLines(RequestText.DESCRIPTION, "Fits the lamp.\nAs the captain asked in D58 A.\n"))
                .extracting(TextFinding::place)
                .containsExactly("line 2 of the description", "line 2 of the description");
    }

    @Test
    void refusesACommitMessageAndTheAddedLinesOfStoryFilesAndDecisionRecords() {
        OutgoingCommit commit = new OutgoingCommit("abc1234def", "Ann <ann@example.org>", "Ann <ann@example.org>", "HRN-3 Fit the lamp the captain asked for",
                List.of("docs/stories/HRN-3.md", "docs/adr/0003-brass-lamps.md", "docs/notes.md", "src/Lamp.java"),
                List.of(
                        new AddedLine("docs/stories/HRN-3.md", 7, "The captain's word: fit the lamp."),
                        new AddedLine("docs/adr/0003-brass-lamps.md", 3, "Brass, as Q10 A settled."),
                        new AddedLine("docs/notes.md", 2, "The captain likes brass."),
                        new AddedLine("src/Lamp.java", 9, "String commander = \"captain\";")));
        Map<String, String> contents = Map.of("docs/stories/HRN-3.md", STORY, "docs/notes.md", "# Notes\n", "docs/adr/0003-brass-lamps.md", "# Lamps\n");

        List<PublishFinding> findings = EdiVocabulary.findIn(new OutgoingChanges(List.of(commit)), (_, path) -> contents.get(path));

        assertThat(findings).extracting(PublishFinding::rule, PublishFinding::location).containsExactly(
                tuple(PublishRule.EDI_VOCABULARY, "message of commit abc1234"),
                tuple(PublishRule.EDI_VOCABULARY, "docs/stories/HRN-3.md:7 in commit abc1234"),
                tuple(PublishRule.EDI_VOCABULARY, "docs/adr/0003-brass-lamps.md:3 in commit abc1234"));
    }

    @Test
    void addsInternalVocabularyToARequestsVerdict() {
        RequestText text = new RequestText("HRN-3 Fit the lamp", "Fits the lamp the captain asked for.\n");

        TextVerdict verdict = TextVerdict.of(text, DenyList.empty(), List.of()).andEdiVocabularyIn(text);

        assertThat(verdict.findings()).extracting(TextFinding::rule, TextFinding::place)
                .containsExactly(tuple(PublishRule.EDI_VOCABULARY, "line 1 of the description"));
    }
}

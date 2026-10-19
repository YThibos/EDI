package io.github.ythibos.edi.adapter.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.board.BoardAttachment;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.testing.Cards;
import tools.jackson.databind.json.JsonMapper;

class LavishResultTest {

    private final LavishResult results = new LavishResult(new JsonMapper());

    @Test
    void readsEveryPromptOfTheListFormWithItsAttachments() throws IOException {
        BoardFeedback.Answered round = answered("list-form.toon");

        assertThat(round.sessionEnded()).isFalse();
        assertThat(round.prompts()).extracting(BoardPrompt::uid).containsExactly("1", "3", "4", "5");
        assertThat(round.prompts().getFirst()).satisfies(annotation -> {
            assertThat(annotation.tag()).isEqualTo("img");
            assertThat(annotation.prompt()).isEqualTo("The glow is too faint in the lower corner, see the two captures.");
            assertThat(annotation.text()).isEqualTo("Deck lighting at 02:00, \"night\" setting");
            assertThat(annotation.attachments()).containsExactly(
                    new BoardAttachment("/home/user/.lavish-axi/attachments/0a1b2c3d4e5f6a7b/11111111.png", "image/png", "image.png"),
                    new BoardAttachment("/home/user/.lavish-axi/attachments/0a1b2c3d4e5f6a7b/22222222.png", "image/png", "image.png"));
        });
        assertThat(round.prompts().get(2).prompt()).isEqualTo("D59 B - and write down why\nthe old fittings stay");
        assertThat(round.prompts().get(3).prompt()).isEqualTo("When checked");
    }

    @Test
    void separatesTheContextDataLavishAppendsFromTheCaptainsWords() throws IOException {
        BoardPrompt choice = answered("list-form.toon").prompts().get(1);

        assertThat(choice.prompt()).isEqualTo("D58: option=A; note: LED, but keep one oil lamp on the bridge");
        assertThat(choice.context()).containsExactly(entry("decision", "D58"), entry("option", "A"), entry("note", "LED, but keep one oil lamp on the bridge"));
        assertThat(choice.decisionNamed()).contains(new DecisionNumber(58));
        assertThat(choice.optionChosen(Cards.card("Which lamp?"))).contains(new OptionKey('A'));
    }

    @Test
    void readsTheTableFormRowByRow() throws IOException {
        BoardFeedback.Answered round = answered("table-form.toon");

        assertThat(round.prompts()).extracting(BoardPrompt::uid).containsExactly("1", "2");
        assertThat(round.prompts().getFirst().context()).containsEntry("choice", "B-oil, C-candles").containsEntry("option", "B");
        assertThat(round.prompts().get(1)).satisfies(annotation -> {
            assertThat(annotation.selector()).isEqualTo("section#s1 > p:nth-of-type(2)");
            assertThat(annotation.prompt()).isEqualTo("Keep the brass, it matches the rail");
            assertThat(annotation.attachments()).isEmpty();
        });
    }

    @Test
    void readsTheLastWordsOfASessionTheCaptainEnded() throws IOException {
        BoardFeedback.Answered round = answered("ended-with-message.toon");

        assertThat(round.sessionEnded()).isTrue();
        assertThat(round.prompts()).singleElement().extracting(BoardPrompt::prompt).isEqualTo("All done for now.");
    }

    @Test
    void readsAnEmptyCloseADisconnectAndAFailureForWhatTheyAre() throws IOException {
        assertThat(results.feedbackIn(fixture("ended-empty.toon"))).isEqualTo(new BoardFeedback.ClosedEmpty());
        assertThat(results.feedbackIn(fixture("disconnected.toon"))).isEqualTo(new BoardFeedback.Disconnected());
        assertThat(results.feedbackIn(fixture("error.toon")))
                .isEqualTo(new BoardFeedback.Failed("Lavish Editor poll response was interrupted (SERVER_ERROR)"));
    }

    @Test
    void refusesAResultWhoseHeaderPromisesMorePromptsThanFollow() throws IOException {
        String truncated = fixture("list-form.toon").replace("prompts[4]:", "prompts[5]:");

        assertThatThrownBy(() -> results.feedbackIn(truncated))
                .isInstanceOf(UnreadableResult.class)
                .hasMessage("Line 4 of the Lavish result cannot be read: its header promises 5 entries and 4 follow.");
    }

    @Test
    void failsClosedOnAStatusItDoesNotKnowOrFeedbackWithoutPrompts() {
        assertThat(results.feedbackIn("session:\n  file: board.html\n  status: rehearsing\n"))
                .isEqualTo(new BoardFeedback.Failed("Lavish answered with a session status EDI does not know: rehearsing."));
        assertThat(results.feedbackIn("session:\n  file: board.html\n  status: feedback\n")).isInstanceOf(BoardFeedback.Failed.class);
    }

    private BoardFeedback.Answered answered(String name) throws IOException {
        BoardFeedback feedback = results.feedbackIn(fixture(name));
        assertThat(feedback).isInstanceOf(BoardFeedback.Answered.class);
        return (BoardFeedback.Answered) feedback;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream content = LavishResultTest.class.getResourceAsStream("/lavish/" + name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

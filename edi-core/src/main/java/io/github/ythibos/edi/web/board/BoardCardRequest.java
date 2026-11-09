package io.github.ythibos.edi.web.board;

import java.util.List;

import io.github.ythibos.edi.domain.board.BoardCard;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.web.decision.CardRequest;

record BoardCardRequest(String question, String title, String context, List<CardRequest.Option> options, CardRequest.Recommend recommend) {

    BoardCard boardCard() {
        DecisionCard card = new CardRequest(title, context, options, recommend, false).card();
        CaptainVoice.requireOutcomes("A decision card", card.wording());
        return new BoardCard(question, card);
    }
}

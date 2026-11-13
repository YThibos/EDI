package io.github.ythibos.edi.web.report;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.primary.RecordTurnItems;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;
import tools.jackson.databind.JsonNode;

@RestController
class TurnController {

    private static final String COMMAND = "edi turn";

    private final RecordTurnItems recordTurnItems;

    TurnController(RecordTurnItems recordTurnItems) {
        this.recordTurnItems = recordTurnItems;
    }

    @PostMapping("/api/turn")
    RecordedTurn recorded(@RequestBody JsonNode items, Caller caller) {
        return new RecordedTurn(recordTurnItems.record(thePrimarysSession(caller), checked(items)));
    }

    private static UUID thePrimarysSession(Caller caller) {
        caller.requirePrimary(COMMAND);
        return caller.claudeSession().orElseThrow(() -> new NotPermitted(COMMAND
                + " records the items of the primary's own turn, so it runs inside its Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID."));
    }

    private static Turn checked(JsonNode items) {
        Turn read = TurnDocument.read(items);
        CaptainVoice.requireOutcomes("The turn's wording in the report", read.wording());
        return read;
    }
}

package io.github.ythibos.edi.adapter.board;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;
import io.github.ythibos.edi.application.port.ResearchBoardHost;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LavishBoardHost implements ResearchBoardHost {

    private final ExternalPrograms programs;
    private final LavishResult results;
    private final String executable;

    public LavishBoardHost(ExternalPrograms programs, JsonMapper json, @Value("${edi.boards.lavish:}") String executable) {
        this.programs = programs;
        this.results = new LavishResult(json);
        this.executable = executable;
    }

    @Override
    public BoardFeedback awaitFeedback(Path board, String listener) {
        try {
            return feedbackOf(polled(board, listener));
        } catch (ProgramFailure unable) {
            if (Thread.currentThread().isInterrupted()) {
                throw unable;
            }
            return new BoardFeedback.Failed(unable.getMessage());
        }
    }

    private ProgramRun polled(Path board, String listener) {
        return programs.runUntilItEnds(ProgramCall.of(Program.LAVISH, executable, board.getParent(),
                List.of("poll", board.toString(), "--owner", listener, "--takeover")));
    }

    private BoardFeedback feedbackOf(ProgramRun poll) {
        if (poll.output().isBlank()) {
            return new BoardFeedback.Failed("lavish-axi poll exited with " + poll.exitCode() + " and no result: " + poll.errors().strip());
        }
        try {
            return results.feedbackIn(poll.output());
        } catch (UnreadableResult unreadable) {
            return new BoardFeedback.Failed(unreadable.getMessage());
        }
    }
}

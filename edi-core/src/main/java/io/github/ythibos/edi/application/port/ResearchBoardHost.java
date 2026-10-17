package io.github.ythibos.edi.application.port;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.board.BoardFeedback;

public interface ResearchBoardHost {

    BoardFeedback awaitFeedback(Path board, String listener);
}

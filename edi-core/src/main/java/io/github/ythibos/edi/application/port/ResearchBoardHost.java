package io.github.ythibos.edi.application.port;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardReply;

public interface ResearchBoardHost {

    BoardFeedback awaitFeedback(Path board, String listener);

    BoardReply reply(Path board, String words);
}

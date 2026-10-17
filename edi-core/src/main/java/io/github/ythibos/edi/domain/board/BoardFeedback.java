package io.github.ythibos.edi.domain.board;

import java.util.List;

public sealed interface BoardFeedback {

    record Answered(List<BoardPrompt> prompts, boolean sessionEnded) implements BoardFeedback {

        public Answered {
            prompts = List.copyOf(prompts);
            if (prompts.isEmpty()) {
                throw new IllegalArgumentException("An answered round carries at least one prompt from the captain.");
            }
        }
    }

    record ClosedEmpty() implements BoardFeedback {
    }

    record Disconnected() implements BoardFeedback {
    }

    record Failed(String reason) implements BoardFeedback {
    }
}

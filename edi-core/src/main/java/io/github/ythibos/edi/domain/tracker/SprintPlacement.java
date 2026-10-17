package io.github.ythibos.edi.domain.tracker;

public sealed interface SprintPlacement {

    record Added(String sprint) implements SprintPlacement {
    }

    record AlreadyInASprint(String sprint) implements SprintPlacement {
    }

    record NoActiveSprint() implements SprintPlacement {
    }

    record Untracked() implements SprintPlacement {
    }
}

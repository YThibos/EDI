package io.github.ythibos.edi.domain.primary;

public enum LaunchOrigin {
    EDI_UP,
    EDI_UP_WITHOUT_THE_BOARD,
    ELSEWHERE;

    public boolean declinesTheBoard() {
        return this == EDI_UP_WITHOUT_THE_BOARD;
    }

    public boolean isEdiUp() {
        return this == EDI_UP;
    }
}

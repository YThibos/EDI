package io.github.ythibos.edi.cli;

enum UpLaunch {
    WITH_THE_BOARD("edi-up"),
    WITHOUT_THE_BOARD("edi-up-no-board");

    static final String VARIABLE = "EDI_PRIMARY_LAUNCH";

    private final String named;

    UpLaunch(String named) {
        this.named = named;
    }

    static UpLaunch asked(boolean withoutTheBoard) {
        return withoutTheBoard ? WITHOUT_THE_BOARD : WITH_THE_BOARD;
    }

    String environmentSetting() {
        return VARIABLE + "=" + named;
    }
}

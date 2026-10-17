package io.github.ythibos.edi.adapter.home;

public record TimeSettings(boolean fleetLedger, boolean requireWorkedLine) {

    static final TimeSettings NONE = new TimeSettings(false, false);
}

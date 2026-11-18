package io.github.ythibos.edi.domain.primary;

public record SessionStart(boolean launched, boolean newSession, Standing standing) {

    public boolean opensTheSitrep(LaunchOrigin origin) {
        return launched && newSession && !origin.declinesTheBoard() && (standing == Standing.PRIMARY || origin.isEdiUp());
    }
}

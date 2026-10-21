package io.github.ythibos.edi.domain.primary;

public record SessionStart(boolean launched, boolean newSession, Standing standing) {

    public boolean opensTheSitrep() {
        return launched && newSession && standing == Standing.PRIMARY;
    }
}

package io.github.ythibos.edi.application.primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.primary.PrimaryOutputStyle;

@Service
public class CheckTheSessionAnchor {

    private final EdiHome home;

    public CheckTheSessionAnchor(EdiHome home) {
        this.home = home;
    }

    public SessionAnchor forSessionStartedIn(Path workingDirectory) {
        return new SessionAnchor(runsInTheHome(workingDirectory) && theOutputStyleHoldsTheAnchor(), home.root(), home.primaryOutputStyle());
    }

    private boolean runsInTheHome(Path workingDirectory) {
        try {
            return workingDirectory != null && Files.isSameFile(workingDirectory, home.root());
        } catch (IOException noSuchDirectory) {
            return false;
        }
    }

    private boolean theOutputStyleHoldsTheAnchor() {
        Path style = home.primaryOutputStyle();
        return Files.isRegularFile(style) && PrimaryOutputStyle.holdsTheAnchor(read(style), PrimaryLayer.read(PrimaryLayer.ANCHOR));
    }

    private static String read(Path style) {
        try {
            return Files.readString(style);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}

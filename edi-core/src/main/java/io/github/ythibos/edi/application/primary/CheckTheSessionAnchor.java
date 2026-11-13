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

    public SessionAnchor forSessionStartedIn(Path projectDirectory) {
        String anchor = PrimaryLayer.read(PrimaryLayer.ANCHOR);
        return new SessionAnchor(runsInTheHome(projectDirectory) && theOutputStyleHolds(anchor), headingOf(anchor), home.root(), home.primaryOutputStyle());
    }

    private boolean runsInTheHome(Path projectDirectory) {
        try {
            return projectDirectory != null && Files.isSameFile(projectDirectory, home.root());
        } catch (IOException noSuchDirectory) {
            return false;
        }
    }

    private boolean theOutputStyleHolds(String anchor) {
        Path style = home.primaryOutputStyle();
        return Files.isRegularFile(style) && PrimaryOutputStyle.holdsTheAnchor(read(style), anchor);
    }

    private static String headingOf(String anchor) {
        return anchor.lines().findFirst().orElseThrow();
    }

    private static String read(Path style) {
        try {
            return Files.readString(style);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}

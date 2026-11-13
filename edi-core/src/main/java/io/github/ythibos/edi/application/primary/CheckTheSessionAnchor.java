package io.github.ythibos.edi.application.primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.primary.HeldAnchor;
import io.github.ythibos.edi.domain.primary.PrimaryOutputStyle;

@Service
public class CheckTheSessionAnchor {

    private final EdiHome home;
    private final Path checkout;

    public CheckTheSessionAnchor(EdiHome home, @Value("${edi.checkout}") Path checkout) {
        this.home = home;
        this.checkout = checkout;
    }

    public SessionAnchor forSessionStartedIn(Path projectDirectory) {
        String anchor = PrimaryLayer.read(PrimaryLayer.ANCHOR);
        return new SessionAnchor(anchorHeldBy(projectDirectory, anchor), PrimaryOutputStyle.headingOf(anchor), home.root(), home.primaryOutputStyle(),
                theCheckoutsAnchor());
    }

    private HeldAnchor anchorHeldBy(Path projectDirectory, String anchor) {
        Path style = home.primaryOutputStyle();
        return runsInTheHome(projectDirectory) && Files.isRegularFile(style) ? PrimaryOutputStyle.anchorHeldIn(read(style), anchor) : HeldAnchor.NONE;
    }

    private boolean runsInTheHome(Path projectDirectory) {
        try {
            return projectDirectory != null && Files.isSameFile(projectDirectory, home.root());
        } catch (IOException noSuchDirectory) {
            return false;
        }
    }

    private static String read(Path style) {
        try {
            return Files.readString(style);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private Optional<Path> theCheckoutsAnchor() {
        Path anchor = checkout.resolve(PrimaryLayer.ANCHOR);
        return Files.isRegularFile(anchor) ? Optional.of(anchor) : Optional.empty();
    }
}

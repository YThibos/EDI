package io.github.ythibos.edi.adapter.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.console.SignInLink;

@Component
public class CaptainsBrowser {

    private static final Duration PATIENCE = Duration.ofSeconds(15);
    private static final String WSL_HOST = "wsl.localhost";

    private final ExternalPrograms programs;
    private final EdiHome home;
    private final String executable;
    private final Path windowsDirectory;
    private final String desktopDisplay;
    private final String wslDistribution;
    private final HandOffPages handOffPages;
    private final Duration patience;

    @Autowired
    public CaptainsBrowser(ExternalPrograms programs, EdiHome home, @Value("${edi.console.browser:}") String executable,
            @Value("${edi.console.windows-directory:/mnt/c}") Path windowsDirectory,
            @Value("${edi.console.desktop-display:${WAYLAND_DISPLAY:${DISPLAY:}}}") String desktopDisplay,
            @Value("${edi.console.wsl-distribution:${WSL_DISTRO_NAME:}}") String wslDistribution) {
        this(programs, home, executable, windowsDirectory, desktopDisplay, wslDistribution, SignInLink.LIFETIME, PATIENCE);
    }

    CaptainsBrowser(ExternalPrograms programs, EdiHome home, String executable, Path windowsDirectory, String desktopDisplay,
            String wslDistribution, Duration handOffLifetime, Duration patience) {
        this.programs = programs;
        this.home = home;
        this.executable = executable;
        this.windowsDirectory = windowsDirectory;
        this.desktopDisplay = desktopDisplay;
        this.wslDistribution = wslDistribution;
        this.handOffPages = new HandOffPages(home.consoleHandOff(), handOffLifetime);
        this.patience = patience;
    }

    public boolean open(URI page) {
        if (!canOpenAPage()) {
            return false;
        }
        Path handOff = handOffPages.leading(page);
        return succeeds(opening(handOff));
    }

    private boolean canOpenAPage() {
        return runsBesideWindows() ? !wslDistribution.isBlank() : hasADesktop();
    }

    private ProgramCall opening(Path handOff) {
        if (runsBesideWindows()) {
            return ProgramCall.of(Program.CMD, executable, windowsDirectory, List.of("/c", "start", seenFromWindows(handOff)));
        }
        return ProgramCall.of(Program.XDG_OPEN, executable, home.root(), List.of(handOff.toString()));
    }

    private boolean succeeds(ProgramCall opening) {
        try {
            return !programs.endsInFailureWithin(opening, patience);
        } catch (ProgramFailure unable) {
            return false;
        }
    }

    private String seenFromWindows(Path handOff) {
        try {
            return new URI("file", WSL_HOST, "/" + wslDistribution + handOff.toAbsolutePath(), null).toASCIIString();
        } catch (URISyntaxException unaddressable) {
            throw new IllegalStateException("The console's hand-off page has no address Windows can open.", unaddressable);
        }
    }

    private boolean runsBesideWindows() {
        return Files.isDirectory(windowsDirectory);
    }

    private boolean hasADesktop() {
        return !desktopDisplay.isBlank();
    }
}

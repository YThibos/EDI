package io.github.ythibos.edi.adapter.browser;

import java.net.URI;
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

@Component
public class CaptainsBrowser {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    private final ExternalPrograms programs;
    private final EdiHome home;
    private final String executable;
    private final Path windowsDirectory;
    private final String desktopDisplay;
    private final Duration patience;

    @Autowired
    public CaptainsBrowser(ExternalPrograms programs, EdiHome home, @Value("${edi.console.browser:}") String executable,
            @Value("${edi.console.windows-directory:/mnt/c}") Path windowsDirectory,
            @Value("${edi.console.desktop-display:${WAYLAND_DISPLAY:${DISPLAY:}}}") String desktopDisplay) {
        this(programs, home, executable, windowsDirectory, desktopDisplay, PATIENCE);
    }

    CaptainsBrowser(ExternalPrograms programs, EdiHome home, String executable, Path windowsDirectory, String desktopDisplay, Duration patience) {
        this.programs = programs;
        this.home = home;
        this.executable = executable;
        this.windowsDirectory = windowsDirectory;
        this.desktopDisplay = desktopDisplay;
        this.patience = patience;
    }

    public boolean open(URI page) {
        return canOpenAPage() && succeeds(opening(page));
    }

    private boolean canOpenAPage() {
        return runsBesideWindows() || hasADesktop();
    }

    private ProgramCall opening(URI page) {
        if (runsBesideWindows()) {
            return ProgramCall.of(Program.CMD, executable, windowsDirectory, List.of("/c", "start", page.toASCIIString()));
        }
        return ProgramCall.of(Program.XDG_OPEN, executable, home.root(), List.of(page.toASCIIString()));
    }

    private boolean succeeds(ProgramCall opening) {
        try {
            return !programs.endsInFailureWithin(opening, patience);
        } catch (ProgramFailure unable) {
            return false;
        }
    }

    private boolean runsBesideWindows() {
        return Files.isDirectory(windowsDirectory);
    }

    private boolean hasADesktop() {
        return !desktopDisplay.isBlank();
    }
}

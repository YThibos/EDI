package io.github.ythibos.edi.adapter.browser;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;

@Component
public class CaptainsBrowser {

    private final ExternalPrograms programs;
    private final String executable;
    private final Path windowsDirectory;

    public CaptainsBrowser(ExternalPrograms programs, @Value("${edi.console.browser:}") String executable,
            @Value("${edi.console.windows-directory:/mnt/c}") Path windowsDirectory) {
        this.programs = programs;
        this.executable = executable;
        this.windowsDirectory = windowsDirectory;
    }

    public boolean open(URI page) {
        if (!Files.isDirectory(windowsDirectory)) {
            return false;
        }
        try {
            return programs.run(ProgramCall.of(Program.CMD, executable, windowsDirectory, List.of("/c", "start", page.toString()))).succeeded();
        } catch (ProgramFailure unable) {
            return false;
        }
    }
}

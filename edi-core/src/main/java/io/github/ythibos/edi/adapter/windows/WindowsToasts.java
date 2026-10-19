package io.github.ythibos.edi.adapter.windows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.presence.CaptainNotice;

@Component
@ImportRuntimeHints(WindowsScripts.class)
public class WindowsToasts {

    private final ExternalPrograms programs;
    private final MachineProfile machine;
    private final String executable;
    private final Path windowsDirectory;

    public WindowsToasts(ExternalPrograms programs, MachineProfile machine, @Value("${edi.windows.powershell:}") String executable,
            @Value("${edi.console.windows-directory:/mnt/c}") Path windowsDirectory) {
        this.programs = programs;
        this.machine = machine;
        this.executable = executable;
        this.windowsDirectory = windowsDirectory;
    }

    public boolean show(CaptainNotice notice) {
        if (!machine.windowsSide().toasts() || !Files.isDirectory(windowsDirectory)) {
            return false;
        }
        String script = WindowsScripts.read(WindowsScripts.TOAST)
                .replace("__TITLE__", WindowsScripts.encodedText(notice.title()))
                .replace("__TEXT__", WindowsScripts.encodedText(notice.text()));
        try {
            return programs.run(ProgramCall.of(Program.POWERSHELL, executable, windowsDirectory, WindowsScripts.encodedCommand(script))).succeeded();
        } catch (ProgramFailure unable) {
            return false;
        }
    }
}

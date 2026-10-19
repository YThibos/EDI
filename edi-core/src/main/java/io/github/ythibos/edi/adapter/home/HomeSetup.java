package io.github.ythibos.edi.adapter.home;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;

@Component
public class HomeSetup {

    private final EdiHome home;

    public HomeSetup(EdiHome home) {
        this.home = home;
    }

    public List<String> missing() {
        return expected().stream().filter(file -> !Files.isRegularFile(file)).map(file -> home.root().relativize(file).toString()).toList();
    }

    private List<Path> expected() {
        return List.of(home.machineProfile(), home.personalConfiguration(), home.projectRegistry(), home.dispatchRules(), home.craftRules(),
                home.memory().resolve("profile.md"), home.memory().resolve("machine.md"), home.memory().resolve("learnings.md"));
    }
}

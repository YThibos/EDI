package io.github.ythibos.edi.application.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

@Component
@ImportRuntimeHints(ExampleConfiguration.ExampleFiles.class)
public class ExampleConfiguration {

    private static final String DISPATCH_RULES = "examples/dispatch-rules.json";
    private static final String MACHINE_PROFILE = "examples/machine.yaml";

    public String dispatchRules() {
        return resource(DISPATCH_RULES);
    }

    public String machineProfile() {
        return resource(MACHINE_PROFILE);
    }

    private static String resource(String name) {
        try (InputStream content = ExampleConfiguration.class.getClassLoader().getResourceAsStream(name)) {
            if (content == null) {
                throw new IllegalStateException(name + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static final class ExampleFiles implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            List.of(DISPATCH_RULES, MACHINE_PROFILE).forEach(file -> hints.resources().registerPattern(file));
        }
    }
}

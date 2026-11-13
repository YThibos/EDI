package io.github.ythibos.edi;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import io.github.ythibos.edi.adapter.home.PrivateHome;
import io.github.ythibos.edi.adapter.process.OwnWorkingDirectory;

final class MoveIntoTheHome implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String HOME = "edi.home";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent prepared) {
        if (buildsTheNativeImage(prepared.getEnvironment())) {
            return;
        }
        Path home = homeIn(prepared.getEnvironment());
        keepTheHomeAsItStoodAtStart(prepared.getEnvironment(), home);
        PrivateHome.createdForItsOwnerAlone(home);
        OwnWorkingDirectory.moveInto(home);
    }

    private static boolean buildsTheNativeImage(ConfigurableEnvironment environment) {
        return environment.getProperty(AbstractAotProcessor.AOT_PROCESSING, Boolean.class, false);
    }

    private static Path homeIn(ConfigurableEnvironment environment) {
        return Path.of(environment.getRequiredProperty(HOME)).toAbsolutePath();
    }

    private static void keepTheHomeAsItStoodAtStart(ConfigurableEnvironment environment, Path home) {
        environment.getPropertySources().addFirst(new MapPropertySource("home as it stood at start", Map.of(HOME, home.toString())));
    }
}

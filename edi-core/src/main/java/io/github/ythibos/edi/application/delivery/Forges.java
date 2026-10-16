package io.github.ythibos.edi.application.delivery;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;

@Component
public class Forges {

    private final List<Forge> forges;

    public Forges(List<Forge> forges) {
        this.forges = List.copyOf(forges);
    }

    public Forge serving(RegisteredForge registered) {
        return forges.stream()
                .filter(forge -> forge.kind() == registered.kind())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("EDI has no adapter for " + registered.kind().word() + " forges."));
    }
}

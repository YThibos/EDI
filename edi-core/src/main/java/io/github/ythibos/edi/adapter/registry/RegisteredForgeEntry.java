package io.github.ythibos.edi.adapter.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredForgeEntry(String kind, String host, String path, String account) {

    RegisteredForge asRegisteredForge() {
        if (kind == null) {
            throw new IllegalArgumentException("A project's forge names its kind, gitlab or github.");
        }
        return new RegisteredForge(ForgeRepository.registered(ForgeKind.named(kind), host, path), account);
    }
}

package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.tracker.Workflow;

class DenyListImportTest {

    private static final List<RegisteredForge> FORGES = List.of(
            new RegisteredForge(new ForgeRepository(ForgeKind.GITLAB, "forge.example.org", "team/lighthouse"), "keeper"),
            new RegisteredForge(new ForgeRepository(ForgeKind.GITLAB, "forge.example.org", "team/harbour"), "keeper"),
            new RegisteredForge(new ForgeRepository(ForgeKind.GITHUB, "github.com", "example-owner/EDI"), "example-owner"));
    private static final TrackerSettings TRACKER = new TrackerSettings(TrackerKind.JIRA, URI.create("https://tracker.example.org"),
            new TrackerSettings.Credentials(Path.of("/secure/tracker-keys.yml"), "TRACKER"), Set.of(), new Workflow(List.of("To Do", "Done")), "Done",
            "Story", null);

    @Test
    void seedsANewListWithThePrivateHostsAccountsAndKeysFileTheHomesKnow() {
        HomeFile list = DenyListImport.listing(Optional.empty(), FORGES, List.of(RemoteAddress.of("git@quay.example.net:team/ledger.git").orElseThrow(),
                RemoteAddress.of("https://github.com/example-owner/lamp.git").orElseThrow()), TRACKER);

        assertThat(list.content()).isEqualTo("""
                # Terms never published, one per line, matched whatever their case.
                forge.example.org
                keeper
                quay.example.net
                tracker.example.org
                tracker-keys.yml
                """);
        assertThat(list.holds()).isEqualTo("5 terms");
    }

    @Test
    void keepsTheCaptainsListAndAddsOnlyWhatItLacks() {
        String captains = "# mine\nFORGE.example.org\nsecret-codename";

        HomeFile list = DenyListImport.listing(Optional.of(captains), FORGES, List.of(), TrackerSettings.none());

        assertThat(list.content()).isEqualTo("# mine\nFORGE.example.org\nsecret-codename\nkeeper\n");
        assertThat(DenyListImport.listing(Optional.of(list.content()), FORGES, List.of(), TrackerSettings.none()).content()).isEqualTo(list.content());
    }
}

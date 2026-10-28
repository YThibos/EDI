package io.github.ythibos.edi.application.migration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import io.github.ythibos.edi.domain.publish.DenyList;
import io.github.ythibos.edi.domain.publish.DeniedTerm;

final class DenyListImport {

    static final String DENY_LIST = "config/publish-denylist";
    private static final Set<String> PUBLIC_FORGES = Set.of("github.com", "gitlab.com", "bitbucket.org");
    private static final String HEADER = "# Terms never published, one per line, matched whatever their case.\n";

    private DenyListImport() {
    }

    static HomeFile listing(Optional<String> current, List<RegisteredForge> forges, List<RemoteAddress> remotes, TrackerSettings tracker) {
        Set<String> listed = termsIn(current.orElse(""));
        List<String> missing = privateNamesIn(forges, remotes, tracker).stream().filter(term -> !listed.contains(term.toLowerCase(Locale.ROOT)))
                .toList();
        String content = current.map(DenyListImport::endingInANewLine).orElse(HEADER) + missing.stream().map(term -> term + "\n").reduce("", String::concat);
        return new HomeFile(DENY_LIST, content, Counted.terms(listed.size() + missing.size()));
    }

    private static Set<String> termsIn(String list) {
        Set<String> terms = new LinkedHashSet<>();
        DenyList.parse(list).terms().stream().map(DeniedTerm::term).forEach(terms::add);
        return terms;
    }

    private static Set<String> privateNamesIn(List<RegisteredForge> forges, List<RemoteAddress> remotes, TrackerSettings tracker) {
        Set<String> names = new LinkedHashSet<>();
        forges.stream().filter(forge -> isPrivate(forge.repository().host())).forEach(forge -> names.addAll(forge.identifyingNames()));
        remotes.stream().map(RemoteAddress::host).filter(DenyListImport::isPrivate).forEach(names::add);
        names.addAll(tracker.identifyingNames());
        return names;
    }

    private static boolean isPrivate(String host) {
        return !PUBLIC_FORGES.contains(host.toLowerCase(Locale.ROOT));
    }

    private static String endingInANewLine(String list) {
        return list.isEmpty() || list.endsWith("\n") ? list : list + "\n";
    }
}

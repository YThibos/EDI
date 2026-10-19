package io.github.ythibos.edi.application.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.application.migration.MarkdownSections.Section;
import io.github.ythibos.edi.domain.AsciiCharacters;

final class MemoryImport {

    static final String PROFILE = "memory/profile.md";
    static final String MACHINE = "memory/machine.md";
    static final String LEARNINGS = "memory/learnings.md";
    static final String ARCHIVE = "memory/archive.md";
    private static final String PROFILE_HEADER = "# Profile\n\nThe captain's rules on every machine.";
    private static final String MACHINE_HEADER = "# This machine\n\nThe captain's rules that hold on this machine only.";
    private static final String LEARNINGS_HEADER = "# Learnings\n\n"
            + "Operational facts, each dated with the day it was last confirmed, with the evidence and the rule it produced.";
    private static final String ARCHIVE_HEADER = "# Archive\n\nRetired memory, kept for reference and never loaded at session start.";
    private static final List<String> TOPICS_OF_THIS_MACHINE = List.of("credential", "secret", "jira", "timesheet", "worklog");
    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final String NAME_CHARACTERS = "._-";

    private final Set<String> projectsOfThisMachine;
    private final List<MemoryRoute> routes = new ArrayList<>();
    private final List<Section> profile = new ArrayList<>();
    private final List<Section> machine = new ArrayList<>();
    private final List<Section> setAside = new ArrayList<>();

    private MemoryImport(Set<String> projectsOfThisMachine) {
        this.projectsOfThisMachine = Set.copyOf(projectsOfThisMachine);
    }

    static MemoryImport of(ForkHome home, Set<String> projectsOfThisMachine) {
        MemoryImport memory = new MemoryImport(projectsOfThisMachine);
        home.captainMemory().map(MarkdownSections::of).ifPresent(captain -> captain.sections().forEach(memory::route));
        home.sharedCaptainMemory().map(MarkdownSections::of).ifPresent(shared -> shared.sections().forEach(memory::keepInTheProfile));
        return memory;
    }

    List<HomeFile> files(ForkHome home) {
        return List.of(
                new HomeFile(PROFILE, document(PROFILE_HEADER, profile), Counted.sections(profile.size())),
                new HomeFile(MACHINE, document(MACHINE_HEADER, machine), Counted.sections(machine.size())),
                learningsFrom(home.learnings()),
                archiveFrom(home.memoryArchive()));
    }

    List<MemoryRoute> routes() {
        return List.copyOf(routes);
    }

    private void route(Section section) {
        if (concernsTheOldHarness(section.heading())) {
            setAside(section);
        } else if (concernsThisMachine(section.heading())) {
            add(section, machine, MACHINE);
        } else {
            add(section, profile, PROFILE);
        }
    }

    private void keepInTheProfile(Section section) {
        add(section, profile, PROFILE);
    }

    private static String document(String header, List<Section> sections) {
        return String.join(PARAGRAPH_BREAK, Stream.concat(Stream.of(header), sections.stream().map(Section::text)).toList()) + "\n";
    }

    private static HomeFile learningsFrom(Optional<String> learnings) {
        MarkdownSections old = MarkdownSections.of(learnings.orElse(""));
        List<String> parts = new ArrayList<>(List.of(LEARNINGS_HEADER));
        if (old.preambleHoldsEntries()) {
            parts.add(old.preambleWithoutTitle());
        }
        old.sections().forEach(section -> parts.add(section.text()));
        return new HomeFile(LEARNINGS, String.join(PARAGRAPH_BREAK, parts) + "\n", Counted.sections(old.sections().size()));
    }

    private HomeFile archiveFrom(Optional<String> memoryArchive) {
        List<String> parts = new ArrayList<>(List.of(memoryArchive.map(String::strip).orElse(ARCHIVE_HEADER)));
        setAside.forEach(section -> parts.add(section.text()));
        String holds = setAside.isEmpty() ? "the old archive" : "the old archive and " + Counted.sections(setAside.size()) + " set aside";
        return new HomeFile(ARCHIVE, String.join(PARAGRAPH_BREAK, parts) + "\n", holds);
    }

    private static boolean concernsTheOldHarness(String heading) {
        return heading.toLowerCase(Locale.ROOT).startsWith(ForkHome.HARNESS_PROJECT);
    }

    private void setAside(Section section) {
        setAside.add(section);
        routes.add(new MemoryRoute(section.heading(), ARCHIVE));
    }

    private boolean concernsThisMachine(String heading) {
        String words = heading.toLowerCase(Locale.ROOT);
        return TOPICS_OF_THIS_MACHINE.stream().anyMatch(words::contains)
                || projectsOfThisMachine.stream().anyMatch(project -> namesTheProject(heading, project));
    }

    private void add(Section section, List<Section> destination, String file) {
        destination.add(section);
        routes.add(new MemoryRoute(section.heading(), file));
    }

    private static boolean namesTheProject(String heading, String project) {
        for (int at = heading.indexOf(project); at >= 0; at = heading.indexOf(project, at + 1)) {
            if (!continuesAName(heading, at - 1) && !continuesAName(heading, at + project.length())) {
                return true;
            }
        }
        return false;
    }

    private static boolean continuesAName(String heading, int index) {
        return index >= 0 && index < heading.length()
                && (AsciiCharacters.isLetterOrDigit(heading.charAt(index)) || NAME_CHARACTERS.indexOf(heading.charAt(index)) >= 0);
    }
}

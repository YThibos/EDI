package io.github.ythibos.edi.application.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.adapter.fork.ForkRoutingRule;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;

final class RoutingRulesImport {

    static final String DISPATCH_RULES = "config/dispatch-rules.json";
    private static final int SHOWN_WORDS = 8;
    private static final List<Carried> CARRIED = List.of(
            Carried.toProject("E.D.I.", RegistryImport.EDI),
            Carried.toCategory("trivial mechanical edit", CategoryId.MECHANICAL),
            Carried.toCategory("captain-feedback micro-fix", CategoryId.REVIEW_FIX),
            Carried.toCategory("full hexagonal stack", CategoryId.IMPLEMENTATION_UNPLANNED),
            Carried.toCategory("broad structural sweep", CategoryId.IMPLEMENTATION_UNPLANNED),
            Carried.toCategory("research or planning", CategoryId.RESEARCH),
            Carried.toCategory("genuinely unknown", CategoryId.RESEARCH),
            Carried.toCategory("procedure, configuration or pipeline question", CategoryId.RESEARCH),
            Carried.toCategory("cause is already established", CategoryId.BUGFIX),
            Carried.toCategory("Angular or frontend component work", CategoryId.IMPLEMENTATION),
            Carried.toCategory("writing tests for already-implemented", CategoryId.IMPLEMENTATION),
            Carried.toCategory("prose documentation", new CategoryId("documentation")),
            Carried.toCategory("CI/CD, build or deployment change", new CategoryId("operations")),
            Carried.toCategory("drives the JIRA system", new CategoryId("operations")),
            Carried.toCategory("onboarding or developer tooling", new CategoryId("operations")),
            Carried.dropped("routine upstream sync"),
            Carried.dropped("shared tracked material"));

    private final Map<CategoryId, List<String>> byCategory = new LinkedHashMap<>();
    private final Map<ProjectName, List<String>> byProject = new LinkedHashMap<>();

    private RoutingRulesImport() {
    }

    static RoutingRulesImport of(List<ForkRoutingRule> rules, MigrationNotes notes) {
        RoutingRulesImport carried = new RoutingRulesImport();
        rules.forEach(rule -> carried.carry(rule, notes));
        return carried;
    }

    Map<CategoryId, List<String>> reasonsByCategory() {
        return Map.copyOf(byCategory);
    }

    Map<ProjectName, List<String>> reasonsByProject() {
        return Map.copyOf(byProject);
    }

    private void carry(ForkRoutingRule rule, MigrationNotes notes) {
        Optional<Carried> carried = CARRIED.stream().filter(candidate -> rule.when().contains(candidate.recognisedBy())).findFirst();
        if (carried.isEmpty()) {
            notes.add(DISPATCH_RULES, "carries nothing of the old rule for \"" + openingOf(rule.when()) + "\": it fits no dispatch category, and its"
                    + " reason stays in the archived crew-dispatch.json");
        } else if (carried.get().isDropped()) {
            notes.add(DISPATCH_RULES, "carries nothing of the old rule for \"" + openingOf(rule.when()) + "\": it routed work on the old harness itself");
        } else if (!rule.why().isBlank()) {
            carried.get().carryInto(rule.why().strip(), byCategory, byProject);
        }
    }

    private static String openingOf(String when) {
        String[] words = when.strip().split("\\s+");
        return words.length <= SHOWN_WORDS ? String.join(" ", words) : String.join(" ", List.of(words).subList(0, SHOWN_WORDS)) + "...";
    }

    private record Carried(String recognisedBy, CategoryId category, ProjectName project) {

        static Carried toCategory(String recognisedBy, CategoryId category) {
            return new Carried(recognisedBy, category, null);
        }

        static Carried toProject(String recognisedBy, ProjectName project) {
            return new Carried(recognisedBy, null, project);
        }

        static Carried dropped(String recognisedBy) {
            return new Carried(recognisedBy, null, null);
        }

        boolean isDropped() {
            return category == null && project == null;
        }

        void carryInto(String reason, Map<CategoryId, List<String>> byCategory, Map<ProjectName, List<String>> byProject) {
            if (category != null) {
                byCategory.computeIfAbsent(category, _ -> new ArrayList<>()).add(reason);
            } else {
                byProject.computeIfAbsent(project, _ -> new ArrayList<>()).add(reason);
            }
        }
    }
}

package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.DispatchCategory;
import io.github.ythibos.edi.domain.dispatch.DispatchRules;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.ModelRoster;
import io.github.ythibos.edi.domain.dispatch.ProjectOverride;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.ProjectName;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RegisterReflectionForBinding({
        DispatchRulesFile.RulesDocument.class,
        DispatchRulesFile.OverrideEntry.class,
        DispatchRulesFile.CategoryEntry.class,
        DispatchRulesFile.RouteEntry.class})
public class DispatchRulesFile {

    private static final String SCHEMA = "edi-dispatch.v2";
    private static final String CARRIED_REASONS = " The old harness's routing rules for this work said: ";
    private static final String CATEGORIES = "categories";
    private static final String PROJECT_OVERRIDES = "projectOverrides";
    private static final String ID = "id";
    private static final String PROJECT = "project";
    private static final String WHY = "why";

    private final Path file;
    private final JsonMapper json;

    public DispatchRulesFile(EdiHome home, JsonMapper json) {
        this.file = home.dispatchRules();
        this.json = json;
    }

    public DispatchRules validatedAgainst(ModelRoster roster) {
        RulesDocument document = document();
        try {
            requireSchema(document);
            return document.asRules().validatedAgainst(roster);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public String withWhatItLacks(String rules, String example, Map<CategoryId, List<String>> byCategory,
            Map<ProjectName, List<String>> byProject) {
        JsonNode document = json.readTree(rules);
        JsonNode examples = json.readTree(example);
        addLacking(document, examples, CATEGORIES, ID);
        addLacking(document, examples, PROJECT_OVERRIDES, PROJECT);
        carryAll(document, CATEGORIES, ID, entry -> byCategory.get(new CategoryId(entry)));
        carryAll(document, PROJECT_OVERRIDES, PROJECT, entry -> byProject.get(new ProjectName(entry)));
        return document.equals(json.readTree(rules)) ? rules : json.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
    }

    private RulesDocument document() {
        String content = ConfigurationFile.contentOf(file)
                .orElseThrow(() -> new InvalidConfiguration(file, "it is missing, and EDI dispatches only by written categories."));
        try {
            return json.readValue(content, RulesDocument.class);
        } catch (JacksonException unreadable) {
            throw new InvalidConfiguration(file, unreadable.getOriginalMessage());
        }
    }

    private static void requireSchema(RulesDocument document) {
        if (!SCHEMA.equals(document.schema())) {
            throw new IllegalArgumentException("its schema is " + document.schema() + ", and EDI reads " + SCHEMA + ".");
        }
    }

    private static void addLacking(JsonNode document, JsonNode examples, String list, String key) {
        List<String> held = document.path(list).valueStream().map(entry -> entry.path(key).asString("")).toList();
        List<JsonNode> lacking = examples.path(list).valueStream().filter(entry -> !held.contains(entry.path(key).asString(""))).toList();
        if (!lacking.isEmpty()) {
            ArrayNode entries = document.has(list) ? (ArrayNode) document.get(list) : ((ObjectNode) document).putArray(list);
            lacking.forEach(entry -> entries.add(entry.deepCopy()));
        }
    }

    private static void carryAll(JsonNode document, String list, String key, Function<String, List<String>> reasonsOf) {
        for (JsonNode entry : document.path(list)) {
            carryInto(entry, reasonsOf.apply(entry.path(key).asString("")));
        }
    }

    private static void carryInto(JsonNode reasoned, List<String> reasons) {
        String why = reasoned.path(WHY).asString("");
        List<String> uncarried = reasons == null ? List.of() : reasons.stream().filter(reason -> !why.contains(reason)).distinct().toList();
        if (!uncarried.isEmpty()) {
            String opening = why.contains(CARRIED_REASONS) ? why + " " : why + CARRIED_REASONS;
            ((ObjectNode) reasoned).put(WHY, opening + String.join(" ", uncarried));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RulesDocument(String schema, List<OverrideEntry> projectOverrides, List<CategoryEntry> categories) {

        DispatchRules asRules() {
            return new DispatchRules(
                    Objects.requireNonNullElse(projectOverrides, List.<OverrideEntry>of()).stream().map(OverrideEntry::asOverride).toList(),
                    Objects.requireNonNullElse(categories, List.<CategoryEntry>of()).stream().map(CategoryEntry::asCategory).toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OverrideEntry(String project, RouteEntry use, String why) {

        ProjectOverride asOverride() {
            return new ProjectOverride(new ProjectName(project), RouteEntry.routeOf(use, "The override for " + project), why);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CategoryEntry(String id, String when, RouteEntry use, String why, String branch, Boolean researchFirst) {

        DispatchCategory asCategory() {
            return new DispatchCategory(
                    new CategoryId(id),
                    when,
                    RouteEntry.routeOf(use, "The category " + id),
                    why,
                    branch == null ? null : BranchKind.named(branch),
                    Boolean.TRUE.equals(researchFirst));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteEntry(String model, String effort) {

        static Route routeOf(RouteEntry use, String usedBy) {
            if (use == null || use.model() == null) {
                throw new IllegalArgumentException(usedBy + " names no model to run on.");
            }
            return new Route(new ModelId(use.model()), use.effort() == null ? null : EffortLevel.withinTheCeiling(use.effort()));
        }
    }
}

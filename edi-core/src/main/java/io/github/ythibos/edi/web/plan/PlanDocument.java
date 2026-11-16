package io.github.ythibos.edi.web.plan;

import java.util.List;
import java.util.function.Function;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.plan.PlanDraft;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.SliceDraft;
import io.github.ythibos.edi.domain.plan.SliceWait;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import tools.jackson.databind.JsonNode;

final class PlanDocument {

    private static final List<String> PLAN_FIELDS = List.of("key", "title", "project", "report", "decision", "capacity", "effort", "slices");
    private static final List<String> SLICE_FIELDS = List.of("number", "title", "category", "key", "body", "wait");

    private PlanDocument() {
    }

    static PlanDraft read(JsonNode plan) {
        requireObject(plan, "The plan is one JSON object with key, title, project, report, decision, capacity, effort and slices.");
        refuseOtherFields(plan, PLAN_FIELDS, "The plan takes only " + String.join(", ", PLAN_FIELDS));
        return new PlanDraft(headingOf(plan), slicesOf(plan.get("slices")));
    }

    private static void requireObject(JsonNode node, String sentence) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(sentence);
        }
    }

    private static void refuseOtherFields(JsonNode node, List<String> fields, String takesOnly) {
        List<String> others = node.propertyNames().stream().filter(name -> !fields.contains(name)).sorted().toList();
        if (!others.isEmpty()) {
            throw new IllegalArgumentException(takesOnly + ", not " + String.join(", ", others) + ".");
        }
    }

    private static PlanHeading headingOf(JsonNode plan) {
        return new PlanHeading(
                parsed(plan, "key", "the plan", WorkKey::parse),
                text(plan, "title", "the plan"),
                parsed(plan, "project", "the plan", ProjectName::new),
                text(plan, "report", "the plan"),
                text(plan, "decision", "the plan"),
                number(plan, "capacity", "the plan"),
                isAbsent(plan.get("effort")) ? null : parsed(plan, "effort", "the plan", EffortLevel::named));
    }

    private static List<SliceDraft> slicesOf(JsonNode slices) {
        if (isAbsent(slices) || !slices.isArray()) {
            throw new IllegalArgumentException("The plan's slices are a list.");
        }
        return slices.values().stream().map(PlanDocument::sliceOf).toList();
    }

    private static SliceDraft sliceOf(JsonNode slice) {
        String where = "a slice of the plan";
        requireObject(slice, "Each slice of the plan is an object with " + String.join(", ", SLICE_FIELDS) + ".");
        refuseOtherFields(slice, SLICE_FIELDS, "A slice of the plan takes only " + String.join(", ", SLICE_FIELDS));
        return new SliceDraft(
                number(slice, "number", where),
                text(slice, "title", where),
                parsed(slice, "category", where, CategoryId::new),
                parsed(slice, "key", where, WorkKey::parse),
                isAbsent(slice.get("body")) ? "" : text(slice, "body", where),
                isAbsent(slice.get("wait")) ? null : parsed(slice, "wait", where, SliceWait::parse));
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static <T> T parsed(JsonNode node, String field, String where, Function<String, T> parse) {
        return parse.apply(text(node, field, where).strip());
    }

    private static String text(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException("The " + field + " of " + where + " is a non-empty text.");
        }
        return value.stringValue();
    }

    private static int number(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt()) {
            throw new IllegalArgumentException("The " + field + " of " + where + " is a whole number.");
        }
        return value.intValue();
    }
}

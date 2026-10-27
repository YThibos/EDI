package io.github.ythibos.edi.application.brief;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.project.NativeImageInput;
import io.github.ythibos.edi.domain.project.ShipBriefRule;
import io.github.ythibos.edi.domain.task.TaskKind;

final class BriefSections {

    private static final String AI_FILES = "(`CLAUDE.md`, `AGENTS.md`, `.claude/`, skills)";

    private BriefSections() {
    }

    static String intent(BriefFacts facts) {
        String notes = facts.task().notes();
        return notes.isBlank() ? "The task records no intent beyond its title: " + facts.task().intake().title() : notes;
    }

    static String spec(BriefFacts facts) {
        String spec = facts.dispatch().spec();
        if (facts.followsAPlan()) {
            return spec.isBlank() ? facts.plan() : facts.plan() + "\n\n" + spec;
        }
        return spec.isBlank() ? "No build instructions beyond the intent." : spec;
    }

    static String projectRules(BriefFacts facts) {
        List<String> rules = new ArrayList<>();
        rules.add("Read the project's `AGENTS.md` or `CLAUDE.md`, and the skills it names, before you change anything.");
        rules.add(aiScaffoldingRule(facts));
        facts.project().shipBriefRules().forEach(rule -> rules.add(shipBriefRule(rule)));
        if (isAShip(facts)) {
            rules.add("Below version 1.0.0 nothing legacy lingers: no deprecation shims and no \"previously\" prose.");
            rules.add("No `-SNAPSHOT` version in any `pom.xml` the branch touches.");
        }
        if (facts.dispatch().expectedChange() != null) {
            rules.addAll(mechanicalRules(facts.dispatch().expectedChange()));
        }
        String bullets = rules.stream().map(rule -> "- " + rule).collect(Collectors.joining("\n"));
        return facts.project().craftRules() ? bullets + "\n\n" + craftRulesBlock(facts) : bullets;
    }

    static String largeBuilds(BriefFacts facts) {
        return facts.project().largeBuilds()
                ? "- Redirect a long build's output to a file and search the file, and kill only a process id you captured."
                : "";
    }

    static String delivery(BriefFacts facts) {
        if (!isAShip(facts)) {
            return "This is a scout: its deliverable is knowledge, so change no tracked file unless the spec asks for it.\n"
                    + "Write the report to `" + facts.reportPath() + "`; a report never authorises implementation.\n"
                    + researchBoard(facts);
        }
        return facts.project().buildsANativeImage() ? nativeBuildRule(facts) + "\n\n" + shipDelivery(facts) : shipDelivery(facts);
    }

    static String definitionOfDone(BriefFacts facts) {
        String done = isAShip(facts) ? shipDone(facts) : scoutDone(facts);
        String timed = facts.asksForTheTimeWorked() ? done + "\n" + workedLine(facts) : done;
        if (facts.dispatch().expectedChange() != null) {
            return timed + "\nEDI accepts the done only when the project's verify command passes and the change touches only the named files;"
                    + " otherwise it hands the task to an implementation worker.";
        }
        return timed;
    }

    private static boolean isAShip(BriefFacts facts) {
        return facts.task().intake().kind() == TaskKind.SHIP;
    }

    private static String researchBoard(BriefFacts facts) {
        return "A research board lives in the task directory and reaches the captain through `edi board arm <board.html> --task " + facts.task().id()
                + " --link <url> --questions <n>`, where the count leaves out its decision card.\n"
                + "A decision the board asks is a card you pass with `--card <card.json>`, never one you only draw: the card as `edi decision raise --help`"
                + " describes it, plus `question`, the `data-lavish-question` of its form, whose answer data is `{question, answer}` with the option's letter.\n"
                + "EDI raises the card on your task and binds it before the captain sees the board, so his choice there is his word for it.";
    }

    private static String nativeBuildRule(BriefFacts facts) {
        return "Run the local native build `" + facts.project().nativeBuild() + "` only when the change can reach the native image: it touches "
                + whatReachesTheNativeImage() + ".\n"
                + "Otherwise " + jvmEvidenceOf(facts) + " the local evidence: " + whereTheSkippedNativeBuildIsExplained(facts);
    }

    private static String whatReachesTheNativeImage() {
        List<String> inputs = Arrays.stream(NativeImageInput.values()).map(NativeImageInput::words).toList();
        return String.join(", ", inputs.subList(0, inputs.size() - 1)) + " or " + inputs.getLast();
    }

    private static String jvmEvidenceOf(BriefFacts facts) {
        return facts.project().verify() == null ? "the JVM build and its tests are" : "`" + facts.project().verify() + "` is";
    }

    private static String whereTheSkippedNativeBuildIsExplained(BriefFacts facts) {
        return switch (facts.delivery()) {
            case DRAFT_REVIEW -> "the merge request body says the native build was not needed and why, and the full optimised native build is left to CI.";
            case LOCAL_BRANCH, LOCAL_APPLY, REPORT -> "`" + facts.reportPath() + "` says the native build was not needed and why.";
        };
    }

    private static String shipDelivery(BriefFacts facts) {
        return switch (facts.delivery()) {
            case DRAFT_REVIEW -> draftReview(facts);
            case LOCAL_BRANCH -> "Commit on `" + facts.dispatch().branch() + "` and push nothing, because the project has no remote.\n"
                    + "When you report done, EDI fetches the branch into the captain's checkout by a ref-only fetch.";
            case LOCAL_APPLY -> "Commit on `" + facts.dispatch().branch() + "` and push nothing: "
                    + "EDI applies the change to the captain's checkout without committing it there.";
            case REPORT -> "This work is delivered by its report: commit nothing, push nothing, and leave the isolated copy as `origin/" + facts.base()
                    + "` holds it.\n"
                    + "Write each change you made and the check that verified it to `" + facts.reportPath() + "`.";
        };
    }

    private static String aiScaffoldingRule(BriefFacts facts) {
        return switch (facts.project().aiScaffolding()) {
            case COMMITTED -> "AI and agent files " + AI_FILES + " are committed like any other file.";
            case LOCAL_ONLY -> "Never create or commit an AI or agent file " + AI_FILES + "; keep any such file out of every commit.";
            case ASK -> "Ask with `needs-decision` before you create or commit an AI or agent file " + AI_FILES + ".";
        };
    }

    private static String shipBriefRule(ShipBriefRule rule) {
        return switch (rule) {
            case DECISION_LOG_ADR -> "Record each architecture or design decision this change makes or changes in the project's decision log, following the log's own rules; "
                    + "a rule, preference or answer you were given is not a decision record.";
            case NO_EXPLANATORY_COMMENTS -> "Code carries no explanatory comments; the decision log carries the why.";
        };
    }

    private static List<String> mechanicalRules(ExpectedChange change) {
        return List.of(
                "This is a mechanical task: make exactly the change the spec states, and nothing else.",
                "Touch only these files: " + change.files().stream().map(file -> "`" + file + "`").collect(Collectors.joining(", ")) + ".",
                "Never edit a decision record, a migration, security configuration, or a test or its expected values.");
    }

    private static String craftRulesBlock(BriefFacts facts) {
        return "The craftsmanship rules below are acceptance criteria for this change:\n\n```text\n" + facts.craftRules().strip() + "\n```";
    }

    private static String draftReview(BriefFacts facts) {
        String craft = facts.project().craftRules() ? ", followed by the craftsmanship rules block, verbatim" : "";
        return "Commit on `" + facts.dispatch().branch() + "`, then validate and deliver through the no-mistakes pipeline from the isolated copy:\n\n"
                + "```sh\nno-mistakes axi run --base-branch " + facts.base() + " --intent \"$(cat '" + facts.intentPath() + "')\"\n```\n\n"
                + "EDI wrote that intent from the Captain's intent and Spec sections above" + craft
                + "; it rewrites the intent when the task is noted, so pass it unchanged.\n"
                + "Answer each gate yourself and never pass `--yes`; an ask-user finding is not yours to answer,"
                + " so report it with `edi status needs-decision --key nm-<run>-<step>` and stop.\n"
                + "The pipeline pushes the branch and opens the merge request as a draft; mark it ready for review once its checks are green.";
    }

    private static String shipDone(BriefFacts facts) {
        return switch (facts.delivery()) {
            case DRAFT_REVIEW -> "CI is green on the merge request for your last commit, and `" + facts.reportPath() + "` holds a short delivery note.\n"
                    + "Then run `edi status done -- <the merge request's full URL>`.\n"
                    + "EDI accepts it only when the merge request from `" + facts.dispatch().branch() + "` to `" + facts.base()
                    + "` is open, as a draft or ready for review, or merged, at your last commit, and the branch adds no `-SNAPSHOT` version.";
            case LOCAL_BRANCH, LOCAL_APPLY -> "Your last commit is on `" + facts.dispatch().branch() + "`, the project's tests pass, and `"
                    + facts.reportPath() + "` holds a short delivery note.\n"
                    + "Then run `edi status done -- <the head commit>`.";
            case REPORT -> "`" + facts.reportPath() + "` records each change and the check that verified it.\n"
                    + "Then run `edi status done -- <a one-line summary>`.\n"
                    + "EDI accepts it only when the report exists and the copy holds no commit or change beyond `origin/" + facts.base()
                    + "`, and then retires the task.";
        };
    }

    private static String scoutDone(BriefFacts facts) {
        return "The report at `" + facts.reportPath() + "` is self-contained: one sentence per line, United Kingdom English, plain dashes,"
                + " and evidence as commands and file references.\n"
                + "Then run `edi status done -- <a one-line summary>`.";
    }

    private static String workedLine(BriefFacts facts) {
        return "Before you report done, end `" + facts.reportPath() + "` with the line `worked: <start> -> <end> = <H>h<MM>m`, the local times"
                + " you started and finished written as `YYYY-MM-DDTHH:MM`.";
    }
}

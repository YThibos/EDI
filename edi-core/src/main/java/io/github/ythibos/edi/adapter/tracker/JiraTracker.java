package io.github.ythibos.edi.adapter.tracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.TrackerSettings;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.AvailableTransition;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TicketProposal;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.tracker.Workflow;
import io.github.ythibos.edi.domain.tracker.WorkflowStep;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class JiraTracker implements Tracker {

    private static final String API = "/rest/api/2";
    private static final String AGILE = "/rest/agile/1.0";
    private static final String TICKET_FIELDS = "summary,status";

    private final MachineProfile machine;
    private final JiraCalls calls;
    private final JsonMapper json;

    public JiraTracker(MachineProfile machine, JiraCalls calls, JsonMapper json) {
        this.machine = machine;
        this.calls = calls;
        this.json = json;
    }

    @Override
    public TrackerKind kind() {
        return TrackerKind.JIRA;
    }

    @Override
    public List<Ticket> findExisting(TicketContext context) {
        TrackerSettings settings = settings();
        return Jql.openTicketsMatching(context)
                .map(jql -> calls.read(settings, API + "/search",
                        Map.of("jql", jql, "fields", TICKET_FIELDS, "maxResults", String.valueOf(TicketProposal.MOST_CANDIDATES)),
                        "the search for open " + context.project() + " tickets"))
                .map(found -> found.path("issues").valueStream().map(JiraTracker::ticketOf).toList())
                .orElse(List.of());
    }

    @Override
    public WorkKey create(TicketDraft draft) {
        TrackerSettings settings = settings();
        ObjectNode fields = json.createObjectNode();
        fields.putObject("project").put("key", draft.project().value());
        fields.putObject("issuetype").put("name", draft.issueType().isEmpty() ? settings.issueType() : draft.issueType());
        fields.put("summary", draft.summary());
        JsonNode created = calls.write(settings, "POST", API + "/issue", withFields(fields), "the new ticket \"" + draft.summary() + "\"");
        return WorkKey.parse(created.path("key").asString());
    }

    @Override
    public void fill(WorkKey key, TicketDraft draft) {
        TrackerSettings settings = settings();
        ObjectNode fields = json.createObjectNode().put("description", draft.description());
        draft.labels().forEach(fields.putArray("labels")::add);
        if (draft.namesAnEpic()) {
            fields.put(settings.requireEpicLinkField(), draft.epic().toString());
        }
        calls.write(settings, "PUT", API + "/issue/" + key, withFields(fields), "the fields of " + key);
        if (draft.namesAPriority()) {
            ObjectNode priority = json.createObjectNode();
            priority.putObject("priority").put("name", draft.priority());
            calls.write(settings, "PUT", API + "/issue/" + key, withFields(priority), "the priority of " + key);
        }
    }

    @Override
    public Ticket verified(WorkKey key) {
        return ticketOf(calls.read(settings(), API + "/issue/" + key, Map.of("fields", TICKET_FIELDS), "the ticket " + key));
    }

    @Override
    public Optional<String> browseAddressOf(WorkKey key) {
        TrackerSettings settings = machine.tracker();
        return settings.isJira() ? Optional.of(settings.baseUrl().toString().replaceAll("/+$", "") + "/browse/" + key) : Optional.empty();
    }

    @Override
    public Optional<Ticket> found(WorkKey key) {
        try {
            return Optional.of(verified(key));
        } catch (NothingFound unknown) {
            return Optional.empty();
        }
    }

    @Override
    public TicketMove transition(WorkKey key, String target) {
        TrackerSettings settings = settings();
        String from = statusOf(settings, key);
        List<String> through = new ArrayList<>();
        WorkflowStep step = nextStep(settings, key, target, from);
        while (step instanceof WorkflowStep.Take take) {
            requireAStepLeftInTheWorkflow(settings.workflow(), key, target, through);
            through.add(statusAfter(settings, key, take.transition()));
            step = nextStep(settings, key, target, through.getLast());
        }
        return outcomeOf(step, key, target, from, through);
    }

    @Override
    public SprintPlacement addToCurrentSprint(WorkKey key) {
        TrackerSettings settings = settings();
        JsonNode sprint = sprintOf(settings, key);
        if (sprint.isObject()) {
            return new SprintPlacement.AlreadyInASprint(nameOf(sprint));
        }
        List<JsonNode> active = activeSprintsOf(settings, key.project());
        return active.isEmpty() ? new SprintPlacement.NoActiveSprint() : placedIn(settings, theOnlyOne(active, key), key);
    }

    @Override
    public void link(WorkKey key, String title, String url) {
        ObjectNode link = json.createObjectNode().put("globalId", url);
        link.putObject("object").put("url", url).put("title", title);
        calls.write(settings(), "POST", API + "/issue/" + key + "/remotelink", link, "the link on " + key);
    }

    private TrackerSettings settings() {
        TrackerSettings settings = machine.tracker();
        if (!settings.isJira()) {
            throw new Refusal("This machine's profile names no JIRA tracker to reach.");
        }
        return settings;
    }

    private ObjectNode withFields(ObjectNode fields) {
        ObjectNode body = json.createObjectNode();
        body.set("fields", fields);
        return body;
    }

    private String statusOf(TrackerSettings settings, WorkKey key) {
        return ticketOf(calls.read(settings, API + "/issue/" + key, Map.of("fields", "status"), "the status of " + key)).status();
    }

    private WorkflowStep nextStep(TrackerSettings settings, WorkKey key, String target, String current) {
        return settings.workflow().stepToward(target, current, transitionsOf(settings, key));
    }

    private List<AvailableTransition> transitionsOf(TrackerSettings settings, WorkKey key) {
        return calls.read(settings, API + "/issue/" + key + "/transitions", Map.of(), "the transitions of " + key)
                .path("transitions").valueStream()
                .map(transition -> new AvailableTransition(transition.path("id").asString(), transition.path("name").asString(""),
                        transition.path("to").path("name").asString("")))
                .toList();
    }

    private static void requireAStepLeftInTheWorkflow(Workflow workflow, WorkKey key, String target, List<String> through) {
        if (through.size() >= workflow.forward().size()) {
            throw new Refusal(key + " took more steps toward " + target + " than its workflow has statuses.");
        }
    }

    private String statusAfter(TrackerSettings settings, WorkKey key, AvailableTransition transition) {
        ObjectNode body = json.createObjectNode();
        body.putObject("transition").put("id", transition.id());
        calls.write(settings, "POST", API + "/issue/" + key + "/transitions", body, "the transition of " + key + " to " + transition.to());
        return statusOf(settings, key);
    }

    private static TicketMove outcomeOf(WorkflowStep step, WorkKey key, String target, String from, List<String> through) {
        return switch (step) {
            case WorkflowStep.Arrived _ -> through.isEmpty() ? new TicketMove.AlreadyThere(from) : new TicketMove.Moved(from, through);
            case WorkflowStep.AlreadyBeyond beyond -> through.isEmpty()
                    ? new TicketMove.AlreadyBeyond(beyond.status())
                    : new TicketMove.Moved(from, through);
            case WorkflowStep.Stuck stuck -> throw new Refusal(key + " cannot move to " + target + ": " + stuck.because());
            case WorkflowStep.Take take -> throw new IllegalStateException("A step to " + take.transition().to() + " is taken, not reported.");
        };
    }

    private JsonNode sprintOf(TrackerSettings settings, WorkKey key) {
        return calls.read(settings, AGILE + "/issue/" + key, Map.of("fields", "sprint"), "the sprint of " + key).path("fields").path("sprint");
    }

    private static String nameOf(JsonNode sprint) {
        return sprint.path("name").asString("");
    }

    private List<JsonNode> activeSprintsOf(TrackerSettings settings, ProjectKey project) {
        Map<Long, JsonNode> active = new LinkedHashMap<>();
        calls.read(settings, AGILE + "/board", Map.of("projectKeyOrId", project.value(), "type", "scrum"), "the scrum boards of " + project)
                .path("values").valueStream()
                .map(board -> board.path("id").asLong())
                .forEach(board -> calls.read(settings, AGILE + "/board/" + board + "/sprint", Map.of("state", "active"), "the active sprints of board " + board)
                        .path("values").valueStream()
                        .forEach(sprint -> active.putIfAbsent(sprint.path("id").asLong(), sprint)));
        return List.copyOf(active.values());
    }

    private static JsonNode theOnlyOne(List<JsonNode> active, WorkKey key) {
        if (active.size() > 1) {
            throw new Refusal("several " + key.project() + " sprints are active (" + active.stream().map(JiraTracker::nameOf).collect(Collectors.joining(", "))
                    + "), so " + key + " joins none of them");
        }
        return active.getFirst();
    }

    private SprintPlacement placedIn(TrackerSettings settings, JsonNode sprint, WorkKey key) {
        ObjectNode body = json.createObjectNode();
        body.putArray("issues").add(key.toString());
        calls.write(settings, "POST", AGILE + "/sprint/" + sprint.path("id").asLong() + "/issue", body, "the sprint " + nameOf(sprint));
        return new SprintPlacement.Added(nameOf(sprint));
    }

    private static Ticket ticketOf(JsonNode issue) {
        JsonNode fields = issue.path("fields");
        return new Ticket(WorkKey.parse(issue.path("key").asString()), fields.path("summary").asString(""), fields.path("status").path("name").asString(""));
    }
}

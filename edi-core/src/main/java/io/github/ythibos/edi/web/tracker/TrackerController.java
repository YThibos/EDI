package io.github.ythibos.edi.web.tracker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.tracker.TicketDrifts;
import io.github.ythibos.edi.application.tracker.WalkTicket;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.tracker.DriftReport;
import io.github.ythibos.edi.domain.tracker.TicketWalk;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class TrackerController {

    private final WalkTicket walkTicket;
    private final TicketDrifts ticketDrifts;

    TrackerController(WalkTicket walkTicket, TicketDrifts ticketDrifts) {
        this.walkTicket = walkTicket;
        this.ticketDrifts = ticketDrifts;
    }

    @PostMapping("/api/tracker/walk")
    WalkedTicket walk(@RequestBody WalkRequest request, Caller caller) {
        caller.requirePrimary("edi tracker walk");
        TicketWalk.Arrived arrived = walkTicket.walk(request.workKey(), request.target());
        return new WalkedTicket(request.workKey(), arrived.from(), arrived.through());
    }

    @GetMapping("/api/tracker/drift")
    DriftReport drift(@RequestParam String project, Caller caller) {
        caller.requirePrimary("edi tracker drift");
        return ticketDrifts.of(new ProjectName(project));
    }
}

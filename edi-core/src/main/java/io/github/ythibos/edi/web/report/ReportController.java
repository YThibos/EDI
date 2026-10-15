package io.github.ythibos.edi.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import tools.jackson.databind.JsonNode;

@RestController
class ReportController {

    private final FleetSnapshotQuery fleet;

    ReportController(FleetSnapshotQuery fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/api/report")
    CaptainReport report(@RequestParam(required = false) Integer paneWidth) {
        return reportOf(Turn.NONE, paneWidth);
    }

    @PostMapping("/api/report")
    CaptainReport report(@RequestBody JsonNode turn, @RequestParam(required = false) Integer paneWidth) {
        return reportOf(TurnDocument.read(turn), paneWidth);
    }

    private CaptainReport reportOf(Turn turn, Integer paneWidth) {
        return new CaptainReport(fleet.snapshot(), turn, paneWidth == null ? ReportWidth.standard() : ReportWidth.forPane(paneWidth));
    }
}

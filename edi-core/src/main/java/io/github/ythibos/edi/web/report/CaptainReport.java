package io.github.ythibos.edi.web.report;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;

public record CaptainReport(FleetSnapshot fleet, Turn turn, ReportWidth width) {
}

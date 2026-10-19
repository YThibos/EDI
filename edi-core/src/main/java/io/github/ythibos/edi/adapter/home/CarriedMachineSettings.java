package io.github.ythibos.edi.adapter.home;

import java.time.Duration;

import io.github.ythibos.edi.domain.dispatch.ModelRoster;

public record CarriedMachineSettings(ModelRoster workers, Boolean fleetLedger, Integer startupBudgetTokens, Duration pauseResurface) {
}

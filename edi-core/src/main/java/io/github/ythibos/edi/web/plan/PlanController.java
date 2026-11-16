package io.github.ythibos.edi.web.plan;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.plan.AddPlan;
import io.github.ythibos.edi.application.plan.PlanQuery;
import io.github.ythibos.edi.application.plan.PlanStandings;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/plans")
class PlanController {

    private final AddPlan addPlan;
    private final PlanQuery planQuery;

    PlanController(AddPlan addPlan, PlanQuery planQuery) {
        this.addPlan = addPlan;
        this.planQuery = planQuery;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation add(@RequestBody JsonNode plan, Caller caller) {
        caller.requirePrimary("edi plan add");
        return new Confirmation.Planned(addPlan.add(PlanDocument.read(plan), caller.actor()));
    }

    @GetMapping("/{key}")
    PlanStandings plan(@PathVariable String key, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("A plan and its slices");
        return planQuery.standings(WorkKey.parse(key));
    }
}

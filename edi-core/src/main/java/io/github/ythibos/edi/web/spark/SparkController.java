package io.github.ythibos.edi.web.spark;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.spark.CatchSpark;
import io.github.ythibos.edi.application.spark.SortSpark;
import io.github.ythibos.edi.domain.spark.SparkAuthor;
import io.github.ythibos.edi.domain.spark.SparkInbox;
import io.github.ythibos.edi.web.access.Caller;

@RestController
@RequestMapping("/api/sparks")
class SparkController {

    private final CatchSpark catchSpark;
    private final SortSpark sortSpark;

    SparkController(CatchSpark catchSpark, SortSpark sortSpark) {
        this.catchSpark = catchSpark;
        this.sortSpark = sortSpark;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CaughtSpark caught(@RequestBody CatchRequest request, Caller caller) {
        caller.requireTheHomeToken("edi spark");
        return new CaughtSpark(catchSpark.caught(request.by(authorOf(caller), caller.claudeSession().orElse(null))));
    }

    @GetMapping
    SparkInbox open(Caller caller) {
        caller.requireThePrimaryOrTheCaptain("The open sparks");
        return sortSpark.inbox();
    }

    @PostMapping("/{number}/backlog")
    SortedSpark backlogged(@PathVariable int number, @RequestBody ItemRequest request, Caller caller) {
        caller.requirePrimary("edi spark backlog");
        return new SortedSpark(sortSpark.backlog(number, request.item()));
    }

    @PostMapping("/{number}/fold")
    SortedSpark folded(@PathVariable int number, @RequestBody ItemRequest request, Caller caller) {
        caller.requirePrimary("edi spark fold");
        return new SortedSpark(sortSpark.foldInto(number, request.item()));
    }

    @PostMapping("/{number}/drop")
    SortedSpark dropped(@PathVariable int number, @RequestBody DropRequest request, Caller caller) {
        caller.requirePrimary("edi spark drop");
        return new SortedSpark(sortSpark.drop(number, request.why()));
    }

    private static SparkAuthor authorOf(Caller caller) {
        return caller.claudeSession().isPresent() ? SparkAuthor.PRIMARY : SparkAuthor.CAPTAIN;
    }
}

package io.github.ythibos.edi.web.task;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.task.Backlog;
import io.github.ythibos.edi.application.task.BacklogQuery;

@RestController
class BacklogController {

    private final BacklogQuery backlog;

    BacklogController(BacklogQuery backlog) {
        this.backlog = backlog;
    }

    @GetMapping("/api/backlog")
    Backlog backlog(@RequestParam(defaultValue = "false") boolean ready) {
        return backlog.backlog(ready);
    }
}

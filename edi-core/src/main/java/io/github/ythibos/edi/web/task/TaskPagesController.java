package io.github.ythibos.edi.web.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class TaskPagesController {

    private static final String READ_ONLY_PAGE = "default-src 'none'; style-src 'unsafe-inline'; img-src 'self'";
    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final EdiHome home;
    private final TaskStore tasks;
    private final Parser markdown = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    TaskPagesController(EdiHome home, TaskStore tasks) {
        this.home = home;
        this.tasks = tasks;
    }

    @GetMapping("/api/tasks/{reference}/pages/report")
    ResponseEntity<String> report(@PathVariable String reference, Caller caller) {
        return page(reference, "report.md", "report", caller);
    }

    @GetMapping("/api/tasks/{reference}/pages/brief")
    ResponseEntity<String> brief(@PathVariable String reference, Caller caller) {
        return page(reference, "brief.md", "brief", caller);
    }

    private ResponseEntity<String> page(String reference, String file, String what, Caller caller) {
        if (!(caller instanceof Caller.Captain)) {
            caller.requirePrimary("Reading a task's " + what);
        }
        TaskId task = tasks.get(TaskReference.parse(reference)).id();
        Path page = home.taskDirectory(task).resolve(file);
        if (!Files.isRegularFile(page)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(TEXT).body(task + " has no " + what + " yet.\n");
        }
        return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noStore()).header("Content-Security-Policy", READ_ONLY_PAGE)
                .body(document(task + " " + what, renderer.render(markdown.parse(contentOf(page)))));
    }

    private static String contentOf(Path page) {
        try {
            return Files.readString(page);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String document(String title, String body) {
        return "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escaped(title) + " · EDI</title><style>body{font-family:system-ui,sans-serif;max-width:52rem;margin:2rem auto;padding:0 1rem;"
                + "line-height:1.5;color:#1b2440;background:#fbfcff}pre,code{font-family:ui-monospace,monospace;background:#eef1fa}pre{padding:.75rem;"
                + "overflow-x:auto}@media (prefers-color-scheme:dark){body{color:#dfe6ff;background:#0a1126}pre,code{background:#111d3d}a{color:#56c8ff}}"
                + "</style></head><body>\n" + body + "</body></html>\n";
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

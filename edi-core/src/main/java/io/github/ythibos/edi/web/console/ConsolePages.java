package io.github.ythibos.edi.web.console;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.github.ythibos.edi.application.access.ConsoleHandOffs;

@Controller
class ConsolePages {

    private static final String HAND_OFF_SPENT = "This page was opened already or has expired; run edi console to open the console again.\n";

    private final ConsoleHandOffs handOffs;

    ConsolePages(ConsoleHandOffs handOffs) {
        this.handOffs = handOffs;
    }

    @GetMapping({"/login", "/fleet", "/week", "/task/{task}"})
    String consolePage() {
        return "forward:/index.html";
    }

    @GetMapping("/" + ConsoleHandOffs.PAGE + "/{code}")
    ResponseEntity<String> handOff(@PathVariable String code) {
        return handOffs.follow(code)
                .map(signIn -> ResponseEntity.status(HttpStatus.SEE_OTHER).location(signIn).cacheControl(CacheControl.noStore()).<String>build())
                .orElseGet(ConsolePages::spent);
    }

    private static ResponseEntity<String> spent() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).cacheControl(CacheControl.noStore()).body(HAND_OFF_SPENT);
    }
}

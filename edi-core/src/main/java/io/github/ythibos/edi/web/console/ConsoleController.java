package io.github.ythibos.edi.web.console;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.access.ConsoleAccess;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.application.primary.OpenTheConsoleOnRequest;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.ConsoleSessionCookie;

import jakarta.servlet.http.HttpServletRequest;

@RestController
class ConsoleController {

    private static final String SIGN_IN_REFUSED = "This sign-in link was used already or has expired; run edi console for a new one.\n";
    private static final String HEADER_MISSING = "Signing in to the console carries the console's own header, " + ConsoleSessionCookie.HEADER + ".\n";

    private final OpenTheConsoleOnRequest openConsole;
    private final ConsoleAccess access;

    ConsoleController(OpenTheConsoleOnRequest openConsole, ConsoleAccess access) {
        this.openConsole = openConsole;
        this.access = access;
    }

    @PostMapping("/api/console/open")
    ConsoleOpening open(HttpServletRequest request, Caller caller) {
        caller.requireTheHomeToken("edi console");
        return openConsole.openFor(caller.claudeSession(), ConsoleAddress.servedTo(request));
    }

    @PostMapping("/api/console/session")
    ResponseEntity<String> signIn(@RequestBody SignInRequest signIn, HttpServletRequest request) {
        if (!ConsoleSessionCookie.headerCarriedBy(request)) {
            return refused(HttpStatus.FORBIDDEN, HEADER_MISSING);
        }
        return signIn.presentedToken().flatMap(access::openSession)
                .map(session -> ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, ConsoleSessionCookie.issued(session)).<String>build())
                .orElseGet(() -> refused(HttpStatus.UNAUTHORIZED, SIGN_IN_REFUSED));
    }

    private static ResponseEntity<String> refused(HttpStatus status, String sentence) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(sentence);
    }
}

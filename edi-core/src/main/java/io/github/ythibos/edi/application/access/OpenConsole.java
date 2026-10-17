package io.github.ythibos.edi.application.access;

import java.net.URI;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;

@Service
public class OpenConsole {

    private static final String SIGN_IN_PAGE = "login";

    private final ConsoleAccess access;
    private final CaptainsBrowser browser;

    public OpenConsole(ConsoleAccess access, CaptainsBrowser browser) {
        this.access = access;
        this.browser = browser;
    }

    public ConsoleOpening open(URI console) {
        URI signIn = URI.create(console.resolve(SIGN_IN_PAGE) + "#" + access.issueSignInLink());
        return browser.open(signIn) ? ConsoleOpening.inTheBrowser(console) : ConsoleOpening.byLink(console, signIn);
    }

    public record ConsoleOpening(URI console, URI signInLink, boolean opened) {

        static ConsoleOpening inTheBrowser(URI console) {
            return new ConsoleOpening(console, null, true);
        }

        static ConsoleOpening byLink(URI console, URI signInLink) {
            return new ConsoleOpening(console, signInLink, false);
        }
    }
}

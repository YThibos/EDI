package io.github.ythibos.edi.application.access;

import java.net.URI;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;

@Service
public class OpenConsole {

    private static final String SIGN_IN_PAGE = "login";

    private final ConsoleAccess access;
    private final ConsoleHandOffs handOffs;
    private final CaptainsBrowser browser;

    public OpenConsole(ConsoleAccess access, ConsoleHandOffs handOffs, CaptainsBrowser browser) {
        this.access = access;
        this.handOffs = handOffs;
        this.browser = browser;
    }

    public ConsoleOpening open(URI console) {
        URI signIn = signInLinkTo(console);
        return browser.open(handOffs.handOff(console, signIn)) ? ConsoleOpening.inTheBrowser(console) : ConsoleOpening.byLink(console, signIn);
    }

    private URI signInLinkTo(URI console) {
        return URI.create(console.resolve(SIGN_IN_PAGE) + "#" + access.issueSignInLink());
    }

    public record ConsoleOpening(URI console, URI signInLink, boolean opened, boolean openedAtSessionStart) {

        public static ConsoleOpening openedAtSessionStart(URI console) {
            return new ConsoleOpening(console, null, true, true);
        }

        static ConsoleOpening inTheBrowser(URI console) {
            return new ConsoleOpening(console, null, true, false);
        }

        static ConsoleOpening byLink(URI console, URI signInLink) {
            return new ConsoleOpening(console, signInLink, false, false);
        }
    }
}

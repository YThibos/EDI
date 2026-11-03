package io.github.ythibos.edi.application.access;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.console.SignInLink;

@Service
public class ConsoleHandOffs {

    public static final String PAGE = "hand-off";

    private final Map<String, HandOff> pending = new ConcurrentHashMap<>();
    private final Clock clock;

    public ConsoleHandOffs(Clock clock) {
        this.clock = clock;
    }

    public URI handOff(URI console, URI signInLink) {
        Instant now = clock.now();
        forgetExpiredAt(now);
        String code = Tokens.drawn();
        pending.put(code, HandOff.leadingTo(signInLink, now));
        return addressOf(console, code);
    }

    public Optional<URI> follow(String code) {
        Instant now = clock.now();
        return Optional.ofNullable(pending.remove(code)).filter(handOff -> handOff.leadsOnAt(now)).map(HandOff::signInLink);
    }

    private void forgetExpiredAt(Instant now) {
        pending.values().removeIf(handOff -> !handOff.leadsOnAt(now));
    }

    private static URI addressOf(URI console, String code) {
        return console.resolve(PAGE + "/" + code);
    }

    private record HandOff(URI signInLink, Instant expiresAt) {

        static HandOff leadingTo(URI signInLink, Instant at) {
            return new HandOff(signInLink, at.plus(SignInLink.LIFETIME));
        }

        boolean leadsOnAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}

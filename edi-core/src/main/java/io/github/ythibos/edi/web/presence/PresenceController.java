package io.github.ythibos.edi.web.presence;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.presence.ChangePresence;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;

@RestController
class PresenceController {

    private static final Set<String> THE_CONSOLES_BUTTONS = Set.of("present", "demo", PresenceRequest.DEMO_OFF);
    private static final String AFK = "/afk";

    private final PresenceQuery presence;
    private final ChangePresence change;
    private final CaptainsWordEvidence captainsWord;

    PresenceController(PresenceQuery presence, ChangePresence change, CaptainsWordEvidence captainsWord) {
        this.presence = presence;
        this.change = change;
        this.captainsWord = captainsWord;
    }

    @GetMapping("/api/presence")
    PresenceView current() {
        return PresenceView.of(presence.current());
    }

    @PostMapping("/api/presence")
    PresenceView change(@RequestBody PresenceRequest request, Caller caller) {
        String mode = request.chosenMode();
        requireAllowed(mode, caller);
        return PresenceView.of(changed(mode, request, caller));
    }

    private static void requireAllowed(String mode, Caller caller) {
        if (caller instanceof Caller.Captain) {
            if (!THE_CONSOLES_BUTTONS.contains(mode)) {
                throw new NotPermitted("The console sets presence to present or to a demo; quiet and away are set in the chat.");
            }
            return;
        }
        caller.requirePrimary("edi presence");
    }

    private Presence changed(String mode, PresenceRequest request, Caller caller) {
        return switch (mode) {
            case "present" -> change.present();
            case "quiet" -> change.quiet();
            case "away" -> away(request.mandateWords(), caller);
            case "demo" -> change.demo();
            case PresenceRequest.DEMO_OFF -> change.demoOff();
            default -> throw new IllegalArgumentException("Presence is present, quiet, away, demo or demo-off, not " + mode + ".");
        };
    }

    private Presence away(String mandate, Caller caller) {
        captainsWord.requireTypedWithItsCommand(caller.callingSession(), AFK, mandate);
        return change.away(mandate);
    }
}

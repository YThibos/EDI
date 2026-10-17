package io.github.ythibos.edi.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import tools.jackson.databind.json.JsonMapper;

@Tag("live")
class JiraLiveCheckTest {

    private static final String HOME = System.getenv("EDI_LIVE_HOME");
    private static final String TICKET = System.getenv("EDI_LIVE_TICKET");

    @Test
    void readsARealTicketAndFindsItByItsOwnWordsWithoutChangingAnything() {
        assumeTrue(HOME != null && TICKET != null, "EDI_LIVE_HOME and EDI_LIVE_TICKET name an EDI home with a JIRA tracker and one of its tickets");
        WorkKey key = WorkKey.parse(TICKET);
        try (JiraCalls calls = new JiraCalls(new JsonMapper())) {
            JiraTracker tracker = new JiraTracker(new MachineProfile(new EdiHome(Path.of(HOME))), calls, new JsonMapper());

            Ticket ticket = tracker.verified(key);

            assertThat(ticket.key()).isEqualTo(key);
            assertThat(ticket.summary()).isNotBlank();
            assertThat(ticket.status()).isNotBlank();
            assertThat(tracker.findExisting(new TicketContext(key.project(), ticket.summary())))
                    .allSatisfy(found -> assertThat(found.key().project()).isEqualTo(key.project()));
        }
    }
}

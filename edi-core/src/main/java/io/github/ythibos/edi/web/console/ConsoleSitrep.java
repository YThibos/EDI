package io.github.ythibos.edi.web.console;

import java.time.Instant;
import java.util.List;

public record ConsoleSitrep(
        String schema,
        Instant generated,
        String zone,
        String machine,
        String address,
        PresenceShown presence,
        List<Card> decisions,
        List<Board> boards,
        List<Review> reviews,
        List<Answer> answered,
        List<Ongoing> ongoing,
        List<Landed> landed,
        int landedTotal,
        List<Charted> charted,
        int chartedTotal) {

    public static final String EDI_SITREP_V1 = "edi-sitrep.v1";

    public ConsoleSitrep {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
        answered = List.copyOf(answered);
        ongoing = List.copyOf(ongoing);
        landed = List.copyOf(landed);
        charted = List.copyOf(charted);
    }

    public record PresenceShown(String mode, boolean demo, String words) {
    }

    public record Card(String number, String task, List<String> projects, String title, String context, List<Option> options,
            Recommendation recommended, Instant raisedAt) {
    }

    public record Option(String key, String text) {
    }

    public record Recommendation(String key, String why) {
    }

    public record Board(String task, String title, List<String> projects, int questions, String link) {
    }

    public record Review(String task, String title, List<String> projects, String url) {
    }

    public record Answer(String number, String task, List<String> projects, String title, String verbatim, String option, String channel,
            Instant answeredAt) {
    }

    public record Ongoing(String task, String title, String kind, List<String> projects, String state, String doing, String draft) {
    }

    public record Landed(String task, String title, String kind, List<String> projects, Instant landedAt, String draft) {
    }

    public record Charted(String task, String title, String kind, List<String> projects, String waiting) {
    }
}

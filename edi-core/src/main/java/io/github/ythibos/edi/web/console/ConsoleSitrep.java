package io.github.ythibos.edi.web.console;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.application.fleet.TaskLinks;

public record ConsoleSitrep(
        String schema,
        Instant generated,
        String zone,
        String machine,
        String address,
        PresenceShown presence,
        LimitStop usageLimit,
        List<Card> decisions,
        List<Board> boards,
        List<Review> reviews,
        List<Question> workerQuestions,
        DealtWithShown dealtWith,
        List<Ongoing> ongoing,
        List<Landed> landed,
        int landedTotal,
        List<Charted> charted,
        int chartedTotal,
        Map<String, TaskLinks> links) {

    public static final String EDI_SITREP_V1 = "edi-sitrep.v1";

    public ConsoleSitrep {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
        workerQuestions = List.copyOf(workerQuestions);
        ongoing = List.copyOf(ongoing);
        landed = List.copyOf(landed);
        charted = List.copyOf(charted);
        links = Map.copyOf(links);
    }

    public record PresenceShown(String mode, boolean demo, String words) {
    }

    public record LimitStop(Instant resetsAt, List<String> tasks) {

        public LimitStop {
            tasks = List.copyOf(tasks);
        }
    }

    public record Card(String number, String task, List<String> projects, String title, String context, List<Option> options,
            Recommendation recommended, boolean urgent, Instant raisedAt, String stops) {
    }

    public record Option(String key, String text) {
    }

    public record Recommendation(String key, String why) {
    }

    public record Board(String call, String task, String title, List<String> projects, int questions, String link, List<BoardDecision> decisions) {

        public Board {
            decisions = List.copyOf(decisions);
        }
    }

    public record BoardDecision(String number, String title) {
    }

    public record Review(String task, String title, List<String> projects, String url, String stackedOn, AfterLanding afterLanding) {
    }

    public record AfterLanding(List<String> steps, String landingOrder) {

        public AfterLanding {
            steps = List.copyOf(steps);
        }
    }

    public record Question(String task, String title, List<String> projects, String question, Instant askedAt) {
    }

    public record DealtWithShown(List<Answer> decisions, List<SettledBoard> boards, List<SettledReview> reviews) {

        public DealtWithShown {
            decisions = List.copyOf(decisions);
            boards = List.copyOf(boards);
            reviews = List.copyOf(reviews);
        }
    }

    public record Answer(String call, String number, String task, List<String> projects, String title, String verbatim, String option, String channel,
            Instant answeredAt) {
    }

    public record SettledBoard(String call, String task, String title, List<String> projects, String link, String state) {
    }

    public record SettledReview(String call, String task, String title, List<String> projects, String url, String state, AfterLanding afterLanding) {
    }

    public record Ongoing(String task, String key, String title, String kind, List<String> projects, String state, String doing, String draft,
            String comingBack, Instant limitResetsAt) {
    }

    public record Landed(String task, String title, String kind, List<String> projects, Instant landedAt, String draft) {
    }

    public record Charted(String task, String title, String kind, List<String> projects, String waiting) {
    }
}

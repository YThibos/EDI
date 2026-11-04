package io.github.ythibos.edi.testing;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SyntheticSecrets {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final GitleaksAllowlists ALLOWLISTS = GitleaksAllowlists.ofTheDefaultConfiguration();
    private static final SyntheticSecrets UNPREDICTABLE = new SyntheticSecrets(new SecureRandom());

    private final RandomGenerator random;

    private SyntheticSecrets(RandomGenerator random) {
        this.random = random;
    }

    static SyntheticSecrets seeded(long seed) {
        return new SyntheticSecrets(new Random(seed));
    }

    public static String awsAccessKeyId() {
        return UNPREDICTABLE.nextAwsAccessKeyId();
    }

    public static String githubToken() {
        return UNPREDICTABLE.nextGithubToken();
    }

    public static String canary() {
        return "canary-" + UNPREDICTABLE.randomFrom(ALPHANUMERIC, 32);
    }

    String nextAwsAccessKeyId() {
        return firstNotAllowlisted(GitleaksAllowlists.AWS_ACCESS_TOKEN, () -> "AK" + "IA" + distinctFrom(BASE32, 16));
    }

    String nextGithubToken() {
        return firstNotAllowlisted(GitleaksAllowlists.GITHUB_PAT, () -> "gh" + "p_" + randomFrom(ALPHANUMERIC, 36));
    }

    private static String firstNotAllowlisted(String rule, Supplier<String> draw) {
        return Stream.generate(draw).filter(secret -> !ALLOWLISTS.allow(rule, secret)).findFirst().orElseThrow();
    }

    private String distinctFrom(String alphabet, int length) {
        List<String> symbols = new ArrayList<>(alphabet.chars().mapToObj(Character::toString).toList());
        Collections.shuffle(symbols, random);
        return String.join("", symbols.subList(0, length));
    }

    private String randomFrom(String alphabet, int length) {
        return random.ints(length, 0, alphabet.length())
                .mapToObj(index -> String.valueOf(alphabet.charAt(index)))
                .collect(Collectors.joining());
    }
}

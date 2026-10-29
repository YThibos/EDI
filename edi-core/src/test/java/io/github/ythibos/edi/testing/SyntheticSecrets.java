package io.github.ythibos.edi.testing;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SyntheticSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private SyntheticSecrets() {
    }

    public static String awsAccessKeyId() {
        return "AK" + "IA" + distinctFrom(BASE32, 16);
    }

    public static String githubToken() {
        return "gh" + "p_" + randomFrom(ALPHANUMERIC, 36);
    }

    public static String canary() {
        return "canary-" + randomFrom(ALPHANUMERIC, 32);
    }

    private static String distinctFrom(String alphabet, int length) {
        List<String> symbols = new ArrayList<>(alphabet.chars().mapToObj(Character::toString).toList());
        Collections.shuffle(symbols, RANDOM);
        return String.join("", symbols.subList(0, length));
    }

    private static String randomFrom(String alphabet, int length) {
        return RANDOM.ints(length, 0, alphabet.length())
                .mapToObj(index -> String.valueOf(alphabet.charAt(index)))
                .collect(Collectors.joining());
    }
}

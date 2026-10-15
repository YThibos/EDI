package io.github.ythibos.edi.testing;

import java.security.SecureRandom;
import java.util.stream.Collectors;

public final class SyntheticSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private SyntheticSecrets() {
    }

    public static String awsAccessKeyId() {
        return "AK" + "IA" + randomFrom(BASE32, 16);
    }

    public static String githubToken() {
        return "gh" + "p_" + randomFrom(ALPHANUMERIC, 36);
    }

    private static String randomFrom(String alphabet, int length) {
        return RANDOM.ints(length, 0, alphabet.length())
                .mapToObj(index -> String.valueOf(alphabet.charAt(index)))
                .collect(Collectors.joining());
    }
}

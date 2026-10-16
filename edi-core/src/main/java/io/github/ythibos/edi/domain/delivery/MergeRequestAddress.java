package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record MergeRequestAddress(ForgeRepository repository, int number) {

    private static final int MOST_DIGITS = 9;

    public MergeRequestAddress {
        Objects.requireNonNull(repository, "A merge request lives in a forge repository.");
        if (number < 1) {
            throw new IllegalArgumentException("A merge request of " + repository + " is numbered from 1: " + number);
        }
    }

    public static MergeRequestAddress parse(String url, ForgeRepository repository) {
        String prefix = repository.mergeRequestPrefix();
        String digits = url != null && url.startsWith(prefix) ? url.substring(prefix.length()) : "";
        if (!AsciiCharacters.isDigits(digits) || digits.length() > MOST_DIGITS) {
            throw notOf(url, repository);
        }
        MergeRequestAddress address = new MergeRequestAddress(repository, Integer.parseInt(digits));
        if (!address.url().equals(url)) {
            throw notOf(url, repository);
        }
        return address;
    }

    public String url() {
        return repository.mergeRequestUrl(number);
    }

    @Override
    public String toString() {
        return url();
    }

    private static IllegalArgumentException notOf(String url, ForgeRepository repository) {
        return new IllegalArgumentException(url + " is not the address of a merge request of " + repository
                + "; it is written as " + repository.mergeRequestUrl(1) + ".");
    }
}

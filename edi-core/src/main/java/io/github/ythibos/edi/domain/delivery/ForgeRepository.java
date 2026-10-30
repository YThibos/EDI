package io.github.ythibos.edi.domain.delivery;

import java.util.Arrays;
import java.util.Objects;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ForgeRepository(ForgeKind kind, String host, String path) {

    private static final String GITHUB_HOST = "github.com";
    private static final String SCHEME = "https://";
    private static final String SEGMENT_SEPARATOR = "/";
    private static final String SEGMENT_SEPARATORS = "._-";

    public ForgeRepository {
        Objects.requireNonNull(kind, "A forge repository names its forge.");
        if (!HostNames.isDnsNameWithAnyPort(host)) {
            throw new IllegalArgumentException("A forge host is a DNS name, with a port if it needs one, such as forge.example.org, not " + host + ".");
        }
        if (!isRepositoryPath(path)) {
            throw new IllegalArgumentException("A forge path names a group or owner and a repository, such as team/service, not " + path + ".");
        }
    }

    public static ForgeRepository registered(ForgeKind kind, String host, String path) {
        if (host == null && kind == ForgeKind.GITHUB) {
            return new ForgeRepository(kind, GITHUB_HOST, path);
        }
        if (host == null) {
            throw new IllegalArgumentException("A " + kind.word() + " forge names its host.");
        }
        return new ForgeRepository(kind, host, path);
    }

    public String url() {
        return SCHEME + host + SEGMENT_SEPARATOR + path;
    }

    public String mergeRequestUrl(int number) {
        return mergeRequestPrefix() + number;
    }

    public boolean holds(String mergeRequestUrl) {
        return mergeRequestUrl.startsWith(mergeRequestPrefix());
    }

    String mergeRequestPrefix() {
        return url() + kind.mergeRequestPath();
    }

    public boolean isAt(RemoteAddress remote) {
        return remote.isTheSameRepositoryAs(new RemoteAddress(host, path));
    }

    @Override
    public String toString() {
        return url();
    }

    private static boolean isRepositoryPath(String path) {
        if (path == null || path.startsWith(SEGMENT_SEPARATOR) || path.endsWith(SEGMENT_SEPARATOR)) {
            return false;
        }
        String[] segments = path.split(SEGMENT_SEPARATOR, -1);
        return segments.length >= 2 && Arrays.stream(segments).allMatch(ForgeRepository::isPathSegment);
    }

    private static boolean isPathSegment(String segment) {
        return !segment.isEmpty()
                && !segment.startsWith(".")
                && segment.chars().allMatch(character -> AsciiCharacters.isLetterOrDigit(character) || SEGMENT_SEPARATORS.indexOf(character) >= 0);
    }
}

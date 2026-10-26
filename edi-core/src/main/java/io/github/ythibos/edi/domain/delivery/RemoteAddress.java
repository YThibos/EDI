package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.Optional;

public record RemoteAddress(String host, String path) {

    private static final List<String> NETWORK_SCHEMES = List.of("https://", "http://", "ssh://", "git://");
    private static final String SCHEME_MARK = "://";
    private static final String GIT_SUFFIX = ".git";
    private static final char USER_MARK = '@';
    private static final char PORT_MARK = ':';
    private static final char PATH_MARK = '/';

    public static Optional<RemoteAddress> of(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return Optional.empty();
        }
        String url = remoteUrl.strip();
        return NETWORK_SCHEMES.stream()
                .filter(url::startsWith)
                .findFirst()
                .map(scheme -> fromAuthorityAndPath(url.substring(scheme.length())))
                .orElseGet(() -> url.contains(SCHEME_MARK) ? Optional.empty() : fromScpLikeAddress(url));
    }

    public boolean isTheSameRepositoryAs(RemoteAddress other) {
        return host.equalsIgnoreCase(other.host) && path.equalsIgnoreCase(other.path);
    }

    private static Optional<RemoteAddress> fromAuthorityAndPath(String rest) {
        int pathStart = rest.indexOf(PATH_MARK);
        if (pathStart < 1) {
            return Optional.empty();
        }
        String host = withoutPort(withoutUser(rest.substring(0, pathStart)));
        return addressOf(host, rest.substring(pathStart + 1));
    }

    private static Optional<RemoteAddress> fromScpLikeAddress(String url) {
        int separator = url.indexOf(PORT_MARK);
        if (separator < 1 || url.indexOf(PATH_MARK) < separator) {
            return Optional.empty();
        }
        return addressOf(withoutUser(url.substring(0, separator)), url.substring(separator + 1));
    }

    private static Optional<RemoteAddress> addressOf(String host, String path) {
        String repository = withoutGitSuffix(trimmedOfSlashes(path));
        return host.isEmpty() || repository.isEmpty() ? Optional.empty() : Optional.of(new RemoteAddress(host, repository));
    }

    private static String withoutUser(String authority) {
        return authority.substring(authority.lastIndexOf(USER_MARK) + 1);
    }

    private static String withoutPort(String hostAndPort) {
        int port = hostAndPort.indexOf(PORT_MARK);
        return port < 0 ? hostAndPort : hostAndPort.substring(0, port);
    }

    private static String trimmedOfSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == PATH_MARK) {
            start++;
        }
        while (end > start && path.charAt(end - 1) == PATH_MARK) {
            end--;
        }
        return path.substring(start, end);
    }

    private static String withoutGitSuffix(String path) {
        return path.endsWith(GIT_SUFFIX) ? path.substring(0, path.length() - GIT_SUFFIX.length()) : path;
    }

    @Override
    public String toString() {
        return host + PATH_MARK + path;
    }
}

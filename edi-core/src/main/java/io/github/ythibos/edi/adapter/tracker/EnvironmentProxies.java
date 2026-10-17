package io.github.ythibos.edi.adapter.tracker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class EnvironmentProxies extends ProxySelector {

    private static final List<String> PROXY_VARIABLES = List.of("https_proxy", "HTTPS_PROXY");
    private static final List<String> EXCEPTION_VARIABLES = List.of("no_proxy", "NO_PROXY");
    private static final List<String> LOOPBACK = List.of("localhost", "127.", "::1", "[::1]");
    private static final int DEFAULT_PROXY_PORT = 80;

    private final Proxy proxy;
    private final List<String> exceptions;

    private EnvironmentProxies(Proxy proxy, List<String> exceptions) {
        this.proxy = proxy;
        this.exceptions = List.copyOf(exceptions);
    }

    static EnvironmentProxies of(Map<String, String> environment) {
        return new EnvironmentProxies(proxyIn(environment), exceptionsIn(environment));
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return proxy == null || isLoopback(host) || isAnException(host) ? List.of(Proxy.NO_PROXY) : List.of(proxy);
    }

    @Override
    @SuppressWarnings("java:S1186") // a failed proxy connection surfaces as the call's own failure, and there is no second proxy to try
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
    }

    private static Proxy proxyIn(Map<String, String> environment) {
        return firstOf(environment, PROXY_VARIABLES)
                .map(EnvironmentProxies::proxyAt)
                .findFirst()
                .orElse(null);
    }

    private static Proxy proxyAt(String written) {
        URI address = URI.create(written.contains("://") ? written : "http://" + written);
        int port = address.getPort() < 0 ? DEFAULT_PROXY_PORT : address.getPort();
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(address.getHost(), port));
    }

    private static List<String> exceptionsIn(Map<String, String> environment) {
        return firstOf(environment, EXCEPTION_VARIABLES)
                .flatMap(written -> Arrays.stream(written.split(",")))
                .map(entry -> entry.strip().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static Stream<String> firstOf(Map<String, String> environment, List<String> names) {
        return names.stream().map(environment::get).filter(value -> value != null && !value.isBlank()).limit(1);
    }

    private static boolean isLoopback(String host) {
        return LOOPBACK.stream().anyMatch(loopback -> loopback.endsWith(".") ? host.startsWith(loopback) : host.equals(loopback));
    }

    private boolean isAnException(String host) {
        return exceptions.stream().anyMatch(entry -> matches(entry, host));
    }

    private static boolean matches(String entry, String host) {
        if (entry.equals("*")) {
            return true;
        }
        if (entry.endsWith(".*")) {
            return host.startsWith(entry.substring(0, entry.length() - 1));
        }
        String domain = entry.startsWith("*.") ? entry.substring(2) : entry.startsWith(".") ? entry.substring(1) : entry;
        return host.equals(domain) || host.endsWith("." + domain);
    }
}

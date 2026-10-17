package io.github.ythibos.edi.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EnvironmentProxiesTest {

    private static final Proxy CORPORATE = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.org", 8080));

    @Test
    void sendsCallsThroughTheProxyTheEnvironmentNames() {
        EnvironmentProxies proxies = EnvironmentProxies.of(Map.of("https_proxy", "http://proxy.example.org:8080"));

        assertThat(proxies.select(URI.create("https://tracker.example.org/rest/api/2/issue/SVC-1"))).containsExactly(CORPORATE);
    }

    @Test
    void goesDirectToLoopbackAndToEveryHostTheExceptionsName() {
        EnvironmentProxies proxies = EnvironmentProxies.of(Map.of("HTTPS_PROXY", "proxy.example.org:8080", "NO_PROXY", "localhost, .intra.example, 10.*"));

        assertThat(proxies.select(URI.create("http://127.0.0.1:4711/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(proxies.select(URI.create("https://tracker.intra.example/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(proxies.select(URI.create("https://10.1.2.3/"))).containsExactly(Proxy.NO_PROXY);
        assertThat(proxies.select(URI.create("https://tracker.example.org/"))).containsExactly(CORPORATE);
    }

    @Test
    void goesDirectWithoutAProxy() {
        assertThat(EnvironmentProxies.of(Map.of()).select(URI.create("https://tracker.example.org/"))).containsExactly(Proxy.NO_PROXY);
    }
}

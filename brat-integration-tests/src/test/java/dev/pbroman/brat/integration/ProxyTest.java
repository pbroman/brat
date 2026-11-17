package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.integration.support.EndToEndTestBase;
import dev.pbroman.brat.integration.support.RecordingProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A proxy set with the JVM's standard {@code http.proxyHost}/{@code http.proxyPort} flags, honoured by
 * the shared handler although it was built before the flags were set.
 *
 * <p>The flags are JVM-global, so they are set and cleared around each test.
 */
class ProxyTest extends EndToEndTestBase {

    private RecordingProxy proxy;
    private String savedHost;
    private String savedPort;

    @BeforeEach
    void startProxy() {
        proxy = new RecordingProxy();
        savedHost = System.getProperty("http.proxyHost");
        savedPort = System.getProperty("http.proxyPort");
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", String.valueOf(proxy.port()));
    }

    @AfterEach
    void stopProxy() {
        restore("http.proxyHost", savedHost);
        restore("http.proxyPort", savedPort);
        proxy.close();
    }

    @Test
    void run_sendsTheRequestThroughTheJvmProxy() {
        // when
        var suite = suite("suites/proxy/through-a-proxy.brat.yaml");
        var result = run(suite, Map.of());

        // then - the answer came from the proxy, and the proxy was asked for the authored host
        assertPassed(result, suite);
        assertThat(proxy.lastTarget()).isEqualTo("http://orders.brat-proxy.invalid/orders");
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}

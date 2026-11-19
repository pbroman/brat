package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which proxy the handler goes through: the one the JVM's default {@link ProxySelector} names, asked
 * on every request.
 * <p>
 * The stub plays the proxy. Each request goes to a host under {@code .invalid}, which never resolves,
 * so a response at all means it went through the stub. The JVM's proxy settings are global, so each
 * test restores them.
 */
class ApacheHttpRequestHandlerProxyTest {

    private static final RequestOptions OPTIONS = new RequestOptions(null, null, Map.of());
    private static final HttpRequestDefinition UNRESOLVABLE =
            new HttpRequestDefinition("http://orders.brat-proxy.invalid/orders", "GET", null, null);

    private final ProxySelector savedSelector = ProxySelector.getDefault();
    private String savedHost;
    private String savedPort;
    private StubHttpServer proxy;
    private ApacheHttpRequestHandler underTest;

    @BeforeEach
    void setUp() {
        savedHost = System.getProperty("http.proxyHost");
        savedPort = System.getProperty("http.proxyPort");
        proxy = new StubHttpServer();
        underTest = new ApacheHttpRequestHandler();
    }

    @AfterEach
    void tearDown() {
        underTest.close();
        proxy.close();
        ProxySelector.setDefault(savedSelector);
        restore("http.proxyHost", savedHost);
        restore("http.proxyPort", savedPort);
    }

    @Test
    void performRequest_returnsAProxysLoginChallengeAsTheResponse() {
        // given - a proxy that wants a login, which this handler does not supply
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", String.valueOf(port()));
        proxy.respond(407, null, "Proxy-Authenticate", "Basic realm=\"corp\"");

        // when
        var response = underTest.performRequest(UNRESOLVABLE, OPTIONS);

        // then - the 407 is what came back, asked once and not answered
        assertThat(response.statusCode()).isEqualTo(407);
        assertThat(proxy.requestCount()).isEqualTo(1);
        assertThat(proxy.lastRequest().headers().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("Proxy-Authorization"));
    }

    @Test
    void performRequest_usesADefaultSelectorReplacedAfterTheHandlerWasBuilt() {
        // given - the handler already exists; an embedding app installs its own selector
        var address = new InetSocketAddress("localhost", port());
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(new Proxy(Proxy.Type.HTTP, address));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // nothing to record
            }
        });
        proxy.respond(200, "via the app's selector");

        // when
        var response = underTest.performRequest(UNRESOLVABLE, OPTIONS);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("via the app's selector");
    }

    private int port() {
        return URI.create(proxy.baseUrl()).getPort();
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}

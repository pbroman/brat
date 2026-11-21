package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which server certificates the handler trusts: the JVM's, so an internal CA is added with the
 * standard {@code javax.net.ssl.trustStore} properties and nothing BRAT-specific.
 * <p>
 * The stub serves a self-signed certificate for {@code localhost} that no default trust store holds.
 * {@code stub-server.p12} is its key, {@code stub-trust.p12} a trust store holding only its
 * certificate. The properties are JVM-global, so each test restores them.
 */
class ApacheHttpRequestHandlerTrustStoreTest {

    private static final RequestOptions OPTIONS = new RequestOptions(null, null, Map.of());
    private static final String PASSWORD = "changeit";
    private static final String[] PROPERTIES = {
        "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType"
    };

    private final Map<String, String> saved = new HashMap<>();
    private HttpsServer server;

    @BeforeEach
    void setUp() throws IOException, GeneralSecurityException {
        for (var property : PROPERTIES) {
            saved.put(property, System.getProperty(property));
        }
        // Every local address, not one for "localhost": the client tries each address "localhost" has, and
        // whichever comes first depends on the machine - a refused one would end the test before TLS.
        server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext()));
        server.createContext("/", exchange -> {
            var body = "trusted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        for (var property : PROPERTIES) {
            if (saved.get(property) == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, saved.get(property));
            }
        }
    }

    @Test
    void performRequest_refusesACertificateTheJvmDoesNotTrust() {
        // given - the JVM's own trust store, which does not hold the stub's certificate
        try (var underTest = new ApacheHttpRequestHandler()) {

            // then - a failed handshake is a transport failure: there is no response
            assertThatThrownBy(() -> underTest.performRequest(get(), OPTIONS))
                    .isInstanceOf(BratException.class)
                    .hasMessageStartingWith("TLS with localhost:")
                    .hasMessageContaining(" failed: ");
        }
    }

    @Test
    void performRequest_trustsACertificateFromTheJvmTrustStore() throws URISyntaxException {
        // given - set before the handler is built, which is when it reads them
        System.setProperty(
                "javax.net.ssl.trustStore", resource("stub-trust.p12").toString());
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

        try (var underTest = new ApacheHttpRequestHandler()) {

            // when
            var response = underTest.performRequest(get(), OPTIONS);

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("trusted");
        }
    }

    private HttpRequestDefinition get() {
        return new HttpRequestDefinition(
                "https://localhost:" + server.getAddress().getPort() + "/", "GET", null, null);
    }

    private static Path resource(String name) throws URISyntaxException {
        var url = Objects.requireNonNull(
                ApacheHttpRequestHandlerTrustStoreTest.class.getResource("/handler/tls/" + name), name);
        return Path.of(url.toURI());
    }

    private static SSLContext serverContext() throws IOException, GeneralSecurityException {
        var keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Objects.requireNonNull(
                ApacheHttpRequestHandlerTrustStoreTest.class.getResourceAsStream("/handler/tls/stub-server.p12"))) {
            keyStore.load(in, PASSWORD.toCharArray());
        }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, PASSWORD.toCharArray());
        var context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }
}

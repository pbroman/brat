package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransportFailuresTest {

    private static final URI LOCAL = URI.create("http://localhost:8080/orders?status=OPEN");

    @Test
    void describe_namesAConnectTimeoutWithTheConnectTimeout() {
        // given - a SocketTimeoutException subclass, so it must be told apart from a read timeout
        var failure = new ConnectTimeoutException("Connect to localhost:8080 timed out");

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message).isEqualTo("Could not connect to localhost:8080 within 30.0 s (the connect timeout)");
    }

    @Test
    void describe_namesAPoolWaitWithTheRequestTimeout() {
        // given
        var failure = new ConnectionRequestTimeoutException("Timeout deadline: 500 MILLISECONDS");

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message)
                .isEqualTo("No connection to localhost:8080 became free within 500 ms (the request's timeout)");
    }

    @Test
    void describe_namesAReadTimeoutWithTheUrlAndTheRequestTimeout() {
        // given
        var failure = new SocketTimeoutException("Read timed out");

        // when
        var message = TransportFailures.describe(failure, LOCAL, 1500, 30_000);

        // then
        assertThat(message)
                .isEqualTo("No response from http://localhost:8080/orders?status=OPEN"
                        + " within 1.5 s (the request's timeout)");
    }

    @Test
    void describe_namesARefusedConnection() {
        // given - the client's own subclass and the JDK's plain one read the same
        var failures = new IOException[] {
            new ConnectException("Connection refused"), new HttpHostConnectException("Connect to … failed")
        };

        for (var failure : failures) {
            // when
            var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

            // then
            assertThat(message)
                    .as(failure.getClass().getSimpleName())
                    .isEqualTo("Could not connect to localhost:8080 — connection refused");
        }
    }

    @Test
    void describe_namesAnUnknownHost() {
        // given
        var uri = URI.create("https://no-such-host.invalid/orders");
        var failure = new UnknownHostException("no-such-host.invalid: Name or service not known");

        // when
        var message = TransportFailures.describe(failure, uri, 500, 30_000);

        // then
        assertThat(message)
                .isEqualTo("Could not connect to no-such-host.invalid:443 — unknown host no-such-host.invalid");
    }

    @Test
    void describe_namesATlsFailureWithItsReason() {
        // given
        var uri = URI.create("https://localhost:8443/orders");
        var failure = new SSLHandshakeException("PKIX path building failed");

        // when
        var message = TransportFailures.describe(failure, uri, 500, 30_000);

        // then
        assertThat(message).isEqualTo("TLS with localhost:8443 failed: PKIX path building failed");
    }

    @Test
    void describe_namesAConnectionClosedWithoutAResponse() {
        // given
        var failure = new NoHttpResponseException("The target server failed to respond");

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message).isEqualTo("localhost:8080 closed the connection without a response");
    }

    @Test
    void describe_namesAnyOtherFailureByItsInnermostCause() {
        // given - how a redirect loop arrives from the client
        var failure = new ClientProtocolException(new RedirectException("Maximum redirects (50) exceeded"));

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message)
                .isEqualTo("Could not perform the request to http://localhost:8080/orders?status=OPEN"
                        + " — RedirectException: Maximum redirects (50) exceeded");
    }

    @Test
    void describe_leavesOutTheMessageOfACauseWithoutOne() {
        // given
        var failure = new IOException();

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message)
                .isEqualTo(
                        "Could not perform the request to http://localhost:8080/orders?status=OPEN" + " — IOException");
    }

    @Test
    void describe_usesTheSchemesDefaultPort() {
        // given
        var servers = Map.of("http://localhost/orders", "localhost:80", "https://localhost/x", "localhost:443");

        for (var entry : servers.entrySet()) {
            // when
            var message = TransportFailures.describe(
                    new ConnectException("Connection refused"), URI.create(entry.getKey()), 500, 30_000);

            // then
            assertThat(message)
                    .as(entry.getKey())
                    .isEqualTo("Could not connect to " + entry.getValue() + " — connection refused");
        }
    }

    @Test
    void describe_neverIncludesUserInfo() {
        // given - a password written into the url must not reach the report through the server's name
        var uri = URI.create("http://alice:hunter2@localhost:8080/orders");

        // when
        var message = TransportFailures.describe(new ConnectException("Connection refused"), uri, 500, 30_000);

        // then
        assertThat(message).isEqualTo("Could not connect to localhost:8080 — connection refused");
    }

    @Test
    void describe_leavesUserInfoOutOfTheUrl() {
        // given - the categories that print the url rather than the server
        var uri = URI.create("http://alice:hunter2@localhost:8080/orders?status=OPEN");
        var failures = new IOException[] {new SocketTimeoutException("Read timed out"), new IOException("odd")};

        for (var failure : failures) {
            // when
            var message = TransportFailures.describe(failure, uri, 500, 30_000);

            // then
            assertThat(message)
                    .as(failure.getClass().getSimpleName())
                    .contains("http://localhost:8080/orders?status=OPEN")
                    .doesNotContain("alice")
                    .doesNotContain("hunter2");
        }
    }

    @Test
    void describe_namesTheInnermostCauseOfATlsFailureWithoutAMessage() {
        // given
        var uri = URI.create("https://localhost:8443/orders");
        var failure = new SSLHandshakeException(null);
        failure.initCause(new CertificateException("certificate expired"));

        // when
        var message = TransportFailures.describe(failure, uri, 500, 30_000);

        // then
        assertThat(message).isEqualTo("TLS with localhost:8443 failed: CertificateException: certificate expired");
    }

    @Test
    void describe_endsOnACycleInTheCauseChain() {
        // given - two exceptions naming each other as cause
        var outer = new IOException("outer");
        var inner = new IllegalStateException("inner");
        outer.initCause(inner);
        inner.initCause(outer);

        // when
        var message = TransportFailures.describe(outer, LOCAL, 500, 30_000);

        // then - the walk stops before revisiting, at the last new cause
        assertThat(message)
                .isEqualTo("Could not perform the request to http://localhost:8080/orders?status=OPEN"
                        + " — IllegalStateException: inner");
    }

    @Test
    void describe_matchesOnlyTheFailureItselfNotItsCause() {
        // given - a refusal wrapped in something else is "anything else", named by the refusal
        var failure = new IOException("wrapped", new ConnectException("Connection refused"));

        // when
        var message = TransportFailures.describe(failure, LOCAL, 500, 30_000);

        // then
        assertThat(message)
                .isEqualTo("Could not perform the request to http://localhost:8080/orders?status=OPEN"
                        + " — ConnectException: Connection refused");
    }
}

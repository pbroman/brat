package dev.pbroman.brat.integration;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.ResourceReader;
import dev.pbroman.brat.integration.stub.StubRequestDefinition;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Selecting a handler by name, and reaching a protocol that arrived on the classpath.
 *
 * <p>What only an assembled run can show: that a name in a suite file reaches a registry built from
 * discovered and hand-wired handlers alike, and that a protocol core has never heard of survives every
 * stage between a document and an assertion — binding, interpolation, execution, and a namespace of
 * its own.
 */
class ProtocolExtensibilityTest extends EndToEndTestBase {

    @Test
    void aRequestIsExecutedByTheHandlerItNames() {
        // given
        var suite = suite("suites/handler-selection.yaml");
        var before = FIXED.calls();

        // when
        var result = run(suite, Map.of());

        // then - one request reached the named handler, the other reached the server
        assertPassed(result, suite);
        assertThat(FIXED.calls() - before).isEqualTo(1);
    }

    @Test
    void aPluginProtocolIsReachableFromASuiteFile() {
        // given - nothing wires the stub protocol: it is on the classpath, and that is the whole
        // registration
        var suite = suite("suites/stub-protocol.yaml");

        // when
        var result = run(suite, Map.of());

        // then
        assertPassed(result, suite);
    }

    @Test
    void aPluginProtocolBindsToItsOwnDefinitionType() {
        // when - the loader's protocol map comes from the registered handlers, so this is the seam
        // between "what may be authored" and "what may be executed"
        var suite = suite("suites/stub-protocol.yaml");

        // then
        assertThat(suite.requests().getFirst().requestDefinition()).isInstanceOf(StubRequestDefinition.class);
    }

    @Test
    void aSuiteNamingAnUnwiredHandlerAbortsTheRun() {
        // given - a suite naming a handler this wiring lacks is not portable to it, and says so
        var suite = new TestSuite(
                "names a handler nobody wired",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "mtls"),
                List.of(request()),
                null);

        // when / then - structural: the run does not start rather than reporting a failed request
        assertThatThrownBy(() -> run(suite, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mtls")
                .hasMessageContaining("fixed")
                .hasMessageContaining("httpclient5");
    }

    @Test
    void aSuiteNamingAnUnknownProtocolFailsToLoad() {
        // given - the failure belongs to loading, not to running: nothing can execute it, so nothing
        // should be able to author it
        var yaml =
                ResourceReader.readFileToString("suites/stub-protocol.yaml").replace("protocol: stub", "protocol: ftp");

        // then
        assertThatThrownBy(() -> suiteFrom(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ftp")
                .hasMessageContaining("stub");
    }

    private static Request request() {
        return new Request(
                "list the users",
                null,
                null,
                null,
                null,
                null,
                new dev.pbroman.brat.core.data.HttpRequestDefinition(baseUrl() + "/all", "GET", null, null, null, null),
                null,
                null);
    }
}

package dev.pbroman.brat.core.runner;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.configdata.AuthInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.HttpRequestDefinitionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.RequestDefinitionInterpolators;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolRegistryTest {

    private static final RequestDefinitionInterpolators HTTP_INTERPOLATORS =
            new RequestDefinitionInterpolators(List.of(new HttpRequestDefinitionInterpolator(new AuthInterpolator())));

    private final HttpRequestDefinition httpRequest =
            new HttpRequestDefinition("http://x/y", "GET", null, null, null, null);

    // ---------- the ladder ----------

    @Test
    void resolve_prefersTheNameTheSuiteAsksFor() {
        // given - two handlers for one protocol is the case the whole mechanism exists for
        var registry = registry(Map.of(), http("httpclient5"), http("mtls"));

        // when
        var handler = registry.resolve(httpRequest, Map.of("http", "mtls"));

        // then
        assertThat(handler.name()).isEqualTo("mtls");
    }

    @Test
    void resolve_fallsBackToTheConfiguredDefault() {
        // given
        var registry = registry(Map.of("http", "mtls"), http("httpclient5"), http("mtls"));

        // when - the suite names nothing
        var handler = registry.resolve(httpRequest, Map.of());

        // then
        assertThat(handler.name()).isEqualTo("mtls");
    }

    @Test
    void resolve_usesTheSoleHandlerWhenThereIsNoDefault() {
        // given - a hand-wired consumer registering one handler and configuring no default must work
        var registry = registry(Map.of(), http("httpclient5"));

        // when
        var handler = registry.resolve(httpRequest, Map.of());

        // then
        assertThat(handler.name()).isEqualTo("httpclient5");
    }

    @Test
    void resolve_failsWhenSeveralHandlersAndNoDefault() {
        // given - never list order: discovery order is unspecified, so position is an order nobody authored
        var registry = registry(Map.of(), http("httpclient5"), http("mtls"));

        // when / then
        assertThatThrownBy(() -> registry.resolve(httpRequest, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("httpclient5")
                .hasMessageContaining("mtls");
    }

    @Test
    void resolve_namesTheCandidatesWhenTheNameIsUnknown() {
        // given - a suite naming a handler this wiring lacks fails loudly rather than falling back
        var registry = registry(Map.of(), http("httpclient5"), http("mtls"));

        // when / then
        assertThatThrownBy(() -> registry.resolve(httpRequest, Map.of("http", "mtsl")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mtsl")
                .hasMessageContaining("Registered: httpclient5, mtls");
    }

    @Test
    void resolve_failsWhenTheProtocolHasNoHandler() {
        // given - a suite authored against a protocol nobody wired
        var registry = registry(Map.of(), http("httpclient5"));
        RequestDefinition ftp = () -> "ftp";

        // when / then
        assertThatThrownBy(() -> registry.resolve(ftp, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ftp")
                .hasMessageContaining("http");
    }

    @Test
    void resolve_throwsForNullArguments() {
        // given
        var registry = registry(Map.of(), http("httpclient5"));

        // then
        assertThatThrownBy(() -> registry.resolve(null, Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> registry.resolve(httpRequest, null)).isInstanceOf(BratException.class);
    }

    // ---------- what it refuses to be built with ----------

    @Test
    void constructor_lastRegistrationWinsForOneName() {
        // given - the overlay that lets a consumer replace a built-in handler while suites keep its name
        var replacement = http("httpclient5");
        var registry = registry(Map.of(), http("httpclient5"), replacement);

        // when
        var handler = registry.resolve(httpRequest, Map.of("http", "httpclient5"));

        // then
        assertThat(handler).isSameAs(replacement);
    }

    @Test
    void constructor_failsWhenHandlersForOneProtocolDisagreeAboutTheDefinitionType() {
        // given - both stay selectable, and a document can bind to only one class per protocol
        RequestHandler<?, ?> odd = new StubHandler("http", "odd", OtherDefinition.class);

        // when / then - last-wins here would be a ClassCastException one layer later
        assertThatThrownBy(() -> registry(Map.of(), http("httpclient5"), odd))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("disagree")
                .hasMessageContaining(OtherDefinition.class.getName());
    }

    @Test
    void constructor_failsWhenAProtocolHasNoInterpolatorForItsDefinition() {
        // given - a plugin shipping a handler and forgetting the interpolator
        RequestHandler<?, ?> other = new StubHandler("stub", "stub", OtherDefinition.class);

        // when / then - named at wiring time, not on its first request
        assertThatThrownBy(() -> new ProtocolRegistry(List.of(other), Map.of(), HTTP_INTERPOLATORS))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("stub")
                .hasMessageContaining("no interpolator");
    }

    @Test
    void constructor_failsWhenADefaultNamesAHandlerNobodyRegistered() {
        // when / then
        assertThatThrownBy(() -> registry(Map.of("http", "mtls"), http("httpclient5")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mtls")
                .hasMessageContaining("Registered: httpclient5");
    }

    @Test
    void constructor_failsWhenAHandlerDeclaresNoKey() {
        // given - a handler with no name cannot be selected, and one with no protocol cannot be found
        RequestHandler<?, ?> nameless = new StubHandler("http", " ", HttpRequestDefinition.class);
        RequestHandler<?, ?> protocolless = new StubHandler(null, "x", HttpRequestDefinition.class);

        // then
        assertThatThrownBy(() -> registry(Map.of(), nameless)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> registry(Map.of(), protocolless)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForNullArguments() {
        assertThatThrownBy(() -> new ProtocolRegistry(null, Map.of(), HTTP_INTERPOLATORS))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new ProtocolRegistry(List.of(), null, HTTP_INTERPOLATORS))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new ProtocolRegistry(List.of(), Map.of(), null))
                .isInstanceOf(BratException.class);
    }

    // ---------- what it publishes ----------

    @Test
    void protocolBindings_saysWhatEachProtocolBindsTo() {
        // given - the loader binds an authored block with this, so you can author what you can execute
        var registry = registry(Map.of(), http("httpclient5"));

        // then
        assertThat(registry.protocolBindings()).containsExactly(Map.entry("http", HttpRequestDefinition.class));
    }

    @Test
    void interpolators_areTheOnesItWasCheckedAgainst() {
        // given
        var registry = registry(Map.of(), http("httpclient5"));

        // then
        assertThat(registry.interpolators()).isSameAs(HTTP_INTERPOLATORS);
    }

    private static ProtocolRegistry registry(Map<String, String> defaults, RequestHandler<?, ?>... handlers) {
        return new ProtocolRegistry(List.of(handlers), defaults, HTTP_INTERPOLATORS);
    }

    private static HttpRequestHandler http(String name) {
        return new HttpRequestHandler() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public HttpResponse performRequest(HttpRequestDefinition requestDefinition) {
                return new HttpResponse(200, Map.of(), null);
            }
        };
    }

    /** A handler whose three keys are whatever a test needs them to be. */
    private record StubHandler(String protocol, String name, Class<? extends RequestDefinition> type)
            implements RequestHandler<RequestDefinition, Object> {

        @Override
        @SuppressWarnings("unchecked")
        public Class<RequestDefinition> definitionType() {
            return (Class<RequestDefinition>) type;
        }

        @Override
        public Object performRequest(RequestDefinition requestDefinition) {
            return null;
        }

        @Override
        public Map<String, Object> responseVars(Object response) {
            return Map.of();
        }
    }

    /** A definition of a protocol core knows nothing about. */
    private static final class OtherDefinition extends ConfigData implements RequestDefinition {

        private OtherDefinition() {
            super(null);
        }

        @Override
        public String protocol() {
            return "stub";
        }
    }
}

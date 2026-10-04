package dev.pbroman.brat.core.runner;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ApacheHttpRequestHandler;
import dev.pbroman.brat.core.launch.Environment;
import dev.pbroman.brat.core.launch.Launch;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class BratTest {

    // Not a lambda any more: a handler carries its own name, so it has two abstract methods.
    private final HttpRequestHandler handler = new HttpRequestHandler() {

        @Override
        public String name() {
            return "test";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            return new HttpResponse(200, Map.of(), "{\"id\": \"7\"}");
        }
    };

    private final Environment environment = Environment.of(Map.of("baseUrl", "http://localhost:8080"), Map.of());

    private static Request request(String name, String url) {
        return new Request(
                name,
                null,
                null,
                null,
                null,
                null,
                null,
                new HttpRequestDefinition(url, "GET", null, null),
                null,
                null);
    }

    private static TestSuite suite(Request... requests) {
        return new TestSuite("suite", null, null, null, null, null, null, null, List.of(requests), null);
    }

    /** An environment whose chain is the one {@link FixedSecretsProviderFactory} builds. */
    private static Environment withFixedSecrets() {
        return new Environment(
                Map.of("baseUrl", "http://localhost:8080"),
                Map.of(),
                new SecretsProviderConfig(Map.of(), List.of(new SecretsSource("fixed", Map.of()))));
    }

    private Brat brat() {
        return Brat.builder().requestHandler(handler).build();
    }

    /**
     * A secrets provider factory that answers {@code recognises} with a fixed verdict.
     *
     * @param type its type
     * @param claims what it answers for every file
     * @return the factory
     */
    private static SecretsProviderFactory recognisingFactory(String type, boolean claims) {
        return new SecretsProviderFactory() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public SecretsProvider create(Map<String, String> params) {
                throw new UnsupportedOperationException("never created here");
            }

            @Override
            public boolean recognises(String content) {
                return claims;
            }
        };
    }

    /** A request naming which handler should execute it. */
    private static Request requestWithHandlers(Map<String, String> requestHandlers) {
        return new Request(
                "r",
                null,
                null,
                null,
                null,
                null,
                requestHandlers,
                new HttpRequestDefinition("http://localhost:8080/x", "GET", null, null),
                null,
                null);
    }

    private static RecordingHandler recordingHandler(String name) {
        return new RecordingHandler(name);
    }

    /** An HTTP handler that answers everything and remembers how often it was asked. */
    private static final class RecordingHandler implements HttpRequestHandler {

        private final String name;
        private int calls;
        private RequestOptions lastOptions;

        private RecordingHandler(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition requestDefinition, RequestOptions options) {
            calls++;
            lastOptions = options;
            return new HttpResponse(200, Map.of(), "{}");
        }
    }

    /** Interpolates an HTTP definition to a fixed URL, so a test can see which one ran. */
    private static final class FixedUrlInterpolator implements RequestDefinitionInterpolator<HttpRequestDefinition> {

        @Override
        public Class<HttpRequestDefinition> definitionType() {
            return HttpRequestDefinition.class;
        }

        @Override
        public HttpRequestDefinition interpolated(
                HttpRequestDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
            return new HttpRequestDefinition(
                    "http://replaced/by-the-builder", target.getMethod(), null, null, null, Map.of());
        }
    }

    /** A handler for a protocol core has no interpolator for. */
    private static final class StubProtocolHandler implements RequestHandler<RequestDefinition, Object> {

        @Override
        public String protocol() {
            return "stub";
        }

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public Class<RequestDefinition> definitionType() {
            return RequestDefinition.class;
        }

        @Override
        public Object performRequest(RequestDefinition requestDefinition, RequestOptions options) {
            return null;
        }

        @Override
        public Map<String, Object> responseVars(Object response) {
            return Map.of();
        }
    }

    // --- wiring ---

    @Test
    void build_suppliesAWorkingHttpHandlerWhenNoneIsAddedOrDiscovered() {
        // given - nothing listens on port 1, so only a real HTTP client gets as far as being refused
        try (var brat = Brat.builder().build()) {

            // when
            var result = brat.run(suite(request("r", "http://127.0.0.1:1/x")), environment);

            // then - the request was attempted and failed on the wire, not for want of a handler
            assertThat(result.requestResults())
                    .singleElement()
                    .satisfies(request -> assertThat(request.status()).isInstanceOf(RequestStatus.Errored.class));
        }
    }

    @Test
    void build_suppliesNoHttpHandlerWhenOneIsAdded() {
        // given - were one supplied beside it, the built-in default would take the request
        var brat = Brat.builder().requestHandler(handler).build();

        // when
        var result = brat.run(suite(request("r", "http://127.0.0.1:1/x")), environment);

        // then
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(request -> assertThat(request.status()).isInstanceOf(RequestStatus.Completed.class));
    }

    @Test
    void build_buildsWithOnlyDiscoveredHandlersAndSuppliesNoOther() {
        // given - no handler added: the command-line case, where every handler comes from a jar
        var brat = Brat.builder()
                .classLoader(closeableHandlerLoader())
                .defaultRequestHandler("http", ClosingHandler.NAME)
                .build();

        // when
        var result = brat.run(suite(request("r", "http://127.0.0.1:1/x")), environment);

        // then
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(request -> assertThat(request.status()).isInstanceOf(RequestStatus.Completed.class));
    }

    @Test
    void build_isUnaffectedByLaterBuilderCalls() {
        // given
        var builder = Brat.builder().requestHandler(handler);
        var built = builder.build();

        // when — the builder stays usable, but what it already produced does not change
        builder.function(BratFunction.of("late", args -> "late"));

        // then
        assertThat(built.run(suite(request("r", "${env.baseUrl}/x")), environment)
                        .requestResults())
                .hasSize(1);
    }

    @Test
    void builder_throwsForANullAddition() {
        // when / then
        assertThatThrownBy(() -> Brat.builder().interpolationRule(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().conditionResolverRule(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().function(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().secretsProviderFactory(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().requestHandler(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().classLoader(null)).isInstanceOf(BratException.class);
    }

    // ---------- the loader this runner agrees with ----------

    @Test
    void loader_bindsWhatThisRunnerCanExecute() {
        // given - the loader's protocols come from the registered handlers, so the two cannot drift
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://localhost:8080/x
                """;

        // when
        var suite = brat().loader().load(yaml);

        // then
        assertThat(suite.requests())
                .singleElement()
                .satisfies(
                        request -> assertThat(request.requestDefinition()).isInstanceOf(HttpRequestDefinition.class));
    }

    @Test
    void environmentReader_letsAFactoryAddedOnTheBuilderRecogniseASecretsFile(@TempDir Path dir) throws IOException {
        // given
        var brat = Brat.builder()
                .requestHandler(handler)
                .secretsProviderFactory(recognisingFactory("enc", true))
                .build();
        Files.writeString(dir.resolve("secrets.yaml"), "enc: ciphertext\n");

        // when
        var environment = brat.environmentReader().read(dir.toString(), Map.of());

        // then
        assertThat(environment.secretsConfig().sources())
                .extracting(SecretsSource::type)
                .containsExactly("enc");
    }

    @Test
    void environmentReader_doesNotAskAFactoryReplacedByOneOfTheSameType(@TempDir Path dir) throws IOException {
        // given - the later 'enc' factory replaces the earlier one, which would have claimed the file
        var brat = Brat.builder()
                .requestHandler(handler)
                .secretsProviderFactory(recognisingFactory("enc", true))
                .secretsProviderFactory(recognisingFactory("enc", false))
                .build();
        Files.writeString(dir.resolve("secrets.yaml"), "enc: ciphertext\n");

        // when
        var environment = brat.environmentReader().read(dir.toString(), Map.of());

        // then - nobody claims it, so it falls back to a plaintext file
        assertThat(environment.secretsConfig().sources())
                .extracting(SecretsSource::type)
                .containsExactly("file");
    }

    @Test
    void loader_rejectsAProtocolThisRunnerHasNoHandlerFor() {
        // given - authorable is the same set as executable: this fails at load, not at execution
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      protocol: ftp
                      url: ftp://x/y
                """;

        // then
        assertThatThrownBy(() -> brat().loader().load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ftp");
    }

    // ---------- selecting a handler ----------

    @Test
    void run_handsTheHandlerTheRequestsOwnTimeoutResolved() {
        // given
        var recording = recordingHandler("test");
        var brat = Brat.builder().requestHandler(recording).build();
        var request = new Request(
                "r",
                null,
                null,
                "${env.timeout}",
                null,
                null,
                null,
                new HttpRequestDefinition("http://localhost:8080/x", "GET", null, null),
                null,
                null);

        // when
        brat.run(suite(request), Environment.of(Map.of("timeout", "1500"), Map.of()));

        // then
        assertThat(recording.lastOptions.timeoutMs()).isEqualTo(1500L);
    }

    @Test
    void run_usesTheHandlerTheRequestNames() {
        // given - two handlers for one protocol, which is what selection by name exists for
        var named = recordingHandler("mtls");
        var brat = Brat.builder()
                .requestHandler(handler)
                .requestHandler(named)
                .defaultRequestHandler("http", "test")
                .build();
        var request = requestWithHandlers(Map.of("http", "mtls"));

        // when
        brat.run(new TestSuite("s", null, null, null, null, null, null, null, List.of(request), null), environment);

        // then
        assertThat(named.calls).isEqualTo(1);
    }

    @Test
    void run_usesTheHandlerTheSuiteNamesWhenTheRequestNamesNone() {
        // given - the suite-level declaration is inherited by a request that says nothing
        var named = recordingHandler("mtls");
        var brat = Brat.builder()
                .requestHandler(handler)
                .requestHandler(named)
                .defaultRequestHandler("http", "test")
                .build();
        var suite = new TestSuite(
                "s",
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "mtls"),
                List.of(request("r", "${env.baseUrl}/x")),
                null);

        // when
        brat.run(suite, environment);

        // then
        assertThat(named.calls).isEqualTo(1);
    }

    @Test
    void run_letsARequestOverrideOneProtocolWithoutDroppingAnother() {
        // given - merged per key, which is why requestHandlers is a map rather than a single name
        var suiteNamed = recordingHandler("mtls");
        var requestNamed = recordingHandler("proxy");
        var brat = Brat.builder()
                .requestHandler(handler)
                .requestHandler(suiteNamed)
                .requestHandler(requestNamed)
                .defaultRequestHandler("http", "test")
                .build();
        var suite = new TestSuite(
                "s",
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "mtls", "ftp", "vsftpd"),
                List.of(requestWithHandlers(Map.of("http", "proxy"))),
                null);

        // when
        brat.run(suite, environment);

        // then - the request's own entry won, and the inherited ftp entry did not disturb it
        assertThat(requestNamed.calls).isEqualTo(1);
        assertThat(suiteNamed.calls).isZero();
    }

    @Test
    void run_abortsWhenASuiteNamesAHandlerThisWiringLacks() {
        // given - a suite naming a handler is not portable to a wiring without it, and fails loudly
        var brat = Brat.builder().requestHandler(handler).build();
        var request = requestWithHandlers(Map.of("http", "mtls"));

        // when / then - structural, so it is not an errored result
        assertThatThrownBy(() -> brat.run(
                        new TestSuite("s", null, null, null, null, null, null, null, List.of(request), null),
                        environment))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mtls");
    }

    @Test
    void build_seedsCoresOwnHandlerAsTheHttpDefault() {
        // given - adding a second handler must not change which client an existing suite uses
        var second = recordingHandler("second");
        var brat = Brat.builder()
                .requestHandler(new ApacheHttpRequestHandler())
                .requestHandler(second)
                .build();

        // when - nothing names a handler, and two are registered
        var result = brat.run(suite(request("r", "http://localhost:1/unreachable")), environment);

        // then - httpclient5 was used, so the connection failed rather than the stub answering
        assertThat(second.calls).isZero();
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Errored.class);
    }

    @Test
    void build_failsWhenADefaultNamesAHandlerNobodyRegistered() {
        // when / then
        assertThatThrownBy(() -> Brat.builder()
                        .requestHandler(handler)
                        .defaultRequestHandler("http", "nope")
                        .build())
                .isInstanceOf(BratException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void build_failsWhenAHandlersProtocolHasNoInterpolator() {
        // given - a plugin shipping a handler and forgetting its interpolator
        var brat = Brat.builder().requestHandler(handler).requestHandler(new StubProtocolHandler());

        // when / then - named at wiring time rather than on the first request of that protocol
        assertThatThrownBy(brat::build).isInstanceOf(BratException.class).hasMessageContaining("stub");
    }

    @Test
    void build_discoversRequestHandlers() {
        // given - its own fixture, so that discovering a handler cannot disturb tests about discovering
        // anything else: two handlers and no default is a failure, which is the point of the ladder
        var root = BratTest.class.getClassLoader().getResource("handler-plugin-fixture/");
        var loader = new URLClassLoader(new URL[] {root}, BratTest.class.getClassLoader());
        var brat = Brat.builder()
                .requestHandler(handler)
                .defaultRequestHandler("http", "test")
                .classLoader(loader)
                .build();
        var request = requestWithHandlers(Map.of("http", "discovered-handler"));

        // when
        var result = brat.run(
                new TestSuite("s", null, null, null, null, null, null, null, List.of(request), null), environment);

        // then - a jar on the classpath is a registration, which is what path 3 exists for
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void builder_addedInterpolatorReplacesCoresForTheSameDefinitionType() {
        // given - the interpolators are collapsed into a type→interpolator map, so the later one wins;
        // this is how a consumer replaces how a definition is prepared without touching core
        var brat = Brat.builder()
                .requestHandler(handler)
                .requestDefinitionInterpolator(new FixedUrlInterpolator())
                .build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/ignored")), environment);

        // then - the definition on the result is the one this interpolator produced
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(requestResult -> assertThat(
                                ((HttpRequestDefinition) requestResult.requestDefinition()).getUrl())
                        .isEqualTo("http://replaced/by-the-builder"));
    }

    @Test
    void build_discoversRequestDefinitionInterpolators() {
        // given - the fixture declares an interpolator for HttpRequestDefinition, which replaces core's
        var root = BratTest.class.getClassLoader().getResource("interpolator-plugin-fixture/");
        var loader = new URLClassLoader(new URL[] {root}, BratTest.class.getClassLoader());
        var brat = Brat.builder().requestHandler(handler).classLoader(loader).build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/ignored")), environment);

        // then - a jar on the classpath registers an interpolator exactly as it registers a handler
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(requestResult -> assertThat(
                                ((HttpRequestDefinition) requestResult.requestDefinition()).getUrl())
                        .isEqualTo(PluginDiscoveryTest.DiscoverableInterpolator.URL));
    }

    @Test
    void builder_throwsForANullInterpolatorOrDefault() {
        assertThatThrownBy(() -> Brat.builder().requestDefinitionInterpolator(null))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultRequestHandler(null, "x"))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultRequestHandler("http", " "))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultRequestHandler("http", null))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultRequestHandler(" ", "x")).isInstanceOf(BratException.class);
    }

    @Test
    void build_discoversPluginsThroughTheConfiguredClassLoader() {
        // given — the fixture declares a BratFunction named "discovered"
        var root = BratTest.class.getClassLoader().getResource("plugin-fixture/");
        var loader = new URLClassLoader(new URL[] {root}, BratTest.class.getClassLoader());
        var brat = Brat.builder().requestHandler(handler).classLoader(loader).build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/${__discovered}")), environment);

        // then — the plugin's function resolved, so discovery reached the function registry
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(requestResult -> assertThat(
                                ((HttpRequestDefinition) requestResult.requestDefinition()).getUrl())
                        .isEqualTo("http://localhost:8080/discovered"));
    }

    @Test
    void builder_addedFunctionReplacesACoreFunctionOfTheSameName() {
        // given — the function registry is collapsed into a name→function map, so the later one wins
        var brat = Brat.builder()
                .requestHandler(handler)
                .function(BratFunction.of("uuid", args -> "fixed-uuid"))
                .build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/${__uuid}")), environment);

        // then
        assertThat(((HttpRequestDefinition) result.requestResults().getFirst().requestDefinition()).getUrl())
                .isEqualTo("http://localhost:8080/fixed-uuid");
    }

    /** A rule claiming one env token, so where it sits relative to the core env rule is observable. */
    private static InterpolationRule envOverride(int priority) {
        return new InterpolationRule() {

            @Override
            public Optional<InterpolationOutcome> outcome(String input, RuntimeData runtimeData) {
                return "${env.baseUrl}".equals(input)
                        ? Optional.of(new InterpolationOutcome("overridden", "overridden"))
                        : Optional.empty();
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

    @Test
    void builder_addedInterpolationRuleLosesToCoreAtEqualPriority() {
        // given — appended after the core rules, and the dispatcher's sort is stable
        var brat = Brat.builder()
                .requestHandler(handler)
                .interpolationRule(envOverride(0))
                .build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/a")), environment);

        // then — the core env rule answered first
        assertThat(((HttpRequestDefinition) result.requestResults().getFirst().requestDefinition()).getUrl())
                .isEqualTo("http://localhost:8080/a");
    }

    @Test
    void builder_addedInterpolationRuleWinsAtAHigherPriority() {
        // given — the only way to override a rule: priorities 0-100 are core's
        var brat = Brat.builder()
                .requestHandler(handler)
                .interpolationRule(envOverride(500))
                .build();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/a")), environment);

        // then
        assertThat(((HttpRequestDefinition) result.requestResults().getFirst().requestDefinition()).getUrl())
                .isEqualTo("overridden/a");
    }

    @Test
    void builder_addedConditionResolverRuleIsConsulted() {
        // given — a func no core rule knows, used as a skip condition
        var brat = Brat.builder()
                .requestHandler(handler)
                .conditionResolverRule(new AlwaysTrueConditionResolverRule())
                .build();
        var skipped = new Request(
                "r",
                null,
                null,
                null,
                new Condition("isAlwaysTrue", "a", null),
                null,
                null,
                new HttpRequestDefinition("${env.baseUrl}/a", "GET", null, null),
                null,
                null);

        // when
        var result = brat.run(suite(skipped), environment);

        // then — the plugin rule answered, so the request never ran
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Skipped.class);
    }

    // --- running ---

    @Test
    void run_producesOneResultPerRequestInOrder() {
        // when
        var result = brat().run(
                        suite(request("first", "${env.baseUrl}/a"), request("second", "${env.baseUrl}/b")),
                        environment);

        // then
        assertThat(result.requestResults())
                .extracting(requestResult -> requestResult.coordinates().name())
                .containsExactly("first", "second");
    }

    @Test
    void run_walksASuiteDeclaringSubSuites() {
        // given - the walk's order is TestSuiteRunner's to pin; this pins only that Brat hands it the tree
        var inner = new TestSuite(
                "inner",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(request("nested", "${env.baseUrl}/b")),
                null);
        var outer = new TestSuite(
                "outer",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(request("top", "${env.baseUrl}/a")),
                List.of(inner));
        var events = new ArrayList<RunEvent>();

        // when
        var result = brat().run(outer, environment, List.of(events::add), new StubRunControl());

        // then
        assertThat(result.requestResults())
                .extracting(requestResult -> requestResult.coordinates().path())
                .containsExactly("outer/top", "outer/inner/nested");
        assertThat(events)
                .filteredOn(RunEvent.SuiteExited.class::isInstance)
                .extracting(event -> ((RunEvent.SuiteExited) event).path())
                .containsExactly("outer/inner", "outer");
    }

    @Test
    void run_failsWithNoRequestResultsWhenItsOnlySubtreeWasAborted() {
        // given - the subtree's setVars reads an env var the launch did not supply
        var admin = new TestSuite(
                "admin",
                null,
                null,
                Map.of("token", "${env.adminToken}"),
                null,
                null,
                null,
                null,
                List.of(request("me", "${env.baseUrl}/me")),
                null);
        var root = new TestSuite("root", null, null, null, null, null, null, null, null, List.of(admin));

        // when
        var result = brat().run(root, environment);

        // then - nothing ran, and that is not green
        assertThat(result.requestResults()).isEmpty();
        assertThat(result.suiteErrors()).singleElement().satisfies(error -> {
            assertThat(error.path()).isEqualTo("root/admin");
            assertThat(error.message()).contains("token", "adminToken");
        });
        assertThat(result.failed()).isTrue();
    }

    @Test
    void run_aSiblingReadingAVarItsSiblingFailedToSetFailsNamingTheCause() {
        // given - "admin" cannot compute the token; "reader" reads it anyway
        var admin = new TestSuite(
                "admin", null, null, Map.of("token", "${env.adminToken}"), null, null, null, null, null, null);
        var reader = new TestSuite(
                "reader",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(request("me", "${env.baseUrl}/me?t=${vars.token}")),
                null);
        var root = new TestSuite("root", null, null, null, null, null, null, null, null, List.of(admin, reader));

        // when
        var result = brat().run(root, environment);

        // then - the tombstone turns a silent "" into an error naming where the var failed
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(requestResult -> assertThat(requestResult.status())
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                                dev.pbroman.brat.core.data.result.RequestStatus.Errored.class))
                        .extracting(dev.pbroman.brat.core.data.result.RequestStatus.Errored::message)
                        .asString()
                        .contains("token", "root/admin"));
    }

    @Test
    void run_resolvesAWaitAfterAndPausesForIt() {
        // given
        var request = new Request(
                "r",
                null,
                null,
                null,
                null,
                null,
                null,
                new HttpRequestDefinition("${env.baseUrl}/x", "GET", null, null),
                null,
                new FlowControl("${env.pause}", null));
        var environment = Environment.of(Map.of("baseUrl", "http://localhost:8080", "pause", "150"), Map.of());

        // when
        var result = brat().run(suite(request), environment);

        // then - the resolved pause is on the result, and the run's time includes it. The run is timed on
        // the wall clock and the pause on the monotonic one, so a few milliseconds between them is allowed
        assertThat(result.requestResults())
                .singleElement()
                .extracting(RequestResult::waitAfterMs)
                .isEqualTo(150L);
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(140L);
    }

    @Test
    void run_throwsForANullArgument() {
        // when / then
        assertThatThrownBy(() -> brat().run(null, environment)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(suite(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void run_throwsForANullArgumentWithListeners() {
        // when / then
        assertThatThrownBy(() -> brat().run(null, environment, List.of(), new StubRunControl()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(suite(), null, List.of(), new StubRunControl()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(suite(), environment, null, new StubRunControl()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(suite(), environment, List.of(), null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void run_buildsTheSecretsChainFromTheEnvironment() {
        // given — a secret resolved through the chain the environment configures proves the chain is
        // built per run, against the run's own namespaces
        var brat = Brat.builder()
                .requestHandler(handler)
                .secretsProviderFactory(new FixedSecretsProviderFactory())
                .build();
        var withSecrets = withFixedSecrets();

        // when
        var result = brat.run(suite(request("r", "${env.baseUrl}/${secrets.token}")), withSecrets);

        // then
        assertThat(((HttpRequestDefinition) result.requestResults().getFirst().requestDefinition()).getUrl())
                .isEqualTo("http://localhost:8080/s3cr3t");
    }

    @Test
    void run_closesTheSecretsChainWhenTheRunEnds() {
        // given — a chain is built per run and may hold a lease or an open file handle
        var factory = new FixedSecretsProviderFactory();
        var brat = Brat.builder()
                .requestHandler(handler)
                .secretsProviderFactory(factory)
                .build();

        // when
        brat.run(suite(request("r", "${env.baseUrl}/${secrets.token}")), withFixedSecrets());

        // then — it does not outlive the run that built it
        assertThat(factory.closed).isTrue();
    }

    // --- events ---

    @Test
    void run_emitsRunStartedFirstAndRunFinishedExactlyOnce() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), new StubRunControl());

        // then — the guarantee a file-writing listener depends on
        assertThat(events.getFirst()).isInstanceOf(RunEvent.RunStarted.class);
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);
        assertThat(events).filteredOn(RunEvent.RunFinished.class::isInstance).hasSize(1);
    }

    @Test
    void run_emitsRequestStartedAndRequestFinishedPerRequest() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), new StubRunControl());

        // then
        assertThat(events).filteredOn(RunEvent.RequestStarted.class::isInstance).hasSize(1);
        assertThat(events)
                .filteredOn(RunEvent.RequestFinished.class::isInstance)
                .hasSize(1);
    }

    @Test
    void run_deliversAnAttemptFinishedPerAttemptOfAPollingRequest() {
        // given — a condition the stub handler's 200 never satisfies, so both attempts are spent
        var events = new ArrayList<RunEvent>();
        var polling = new Request(
                "poll",
                null,
                null,
                null,
                null,
                null,
                null,
                new HttpRequestDefinition("${env.baseUrl}/a", "GET", null, null),
                null,
                new FlowControl(
                        null,
                        new RepeatUntil(
                                new Condition("isEqualTo", "${response.statusCode}", "500"), "2", null, null, null)));

        // when
        brat().run(suite(polling), environment, List.of(events::add), new StubRunControl());

        // then — reported as they happen, which is the whole reason a poll emits anything
        assertThat(events).filteredOn(AttemptFinished.class::isInstance).hasSize(2);
    }

    @Test
    void run_deliversRunFinishedWithCancelledSetWhenStopped() {
        // given — cancelled before the first check
        var events = new ArrayList<RunEvent>();
        var control = new StubRunControl();
        control.cancel();

        // when
        var result = brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), control);

        // then
        assertThat(result.cancelled()).isTrue();
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);
        assertThat(((RunEvent.RunFinished) events.getLast()).result().cancelled())
                .isTrue();
    }

    @Test
    void run_stopsBetweenRequestsRatherThanMidRequest() {
        // given — a listener that cancels as soon as the first request finishes
        var control = new StubRunControl();
        RunListener canceller = event -> {
            if (event instanceof RunEvent.RequestFinished) {
                control.cancel();
            }
        };

        // when
        var result = brat().run(
                        suite(request("first", "${env.baseUrl}/a"), request("second", "${env.baseUrl}/b")),
                        environment,
                        List.of(canceller),
                        control);

        // then — the first request completed, the second never started
        assertThat(result.requestResults())
                .extracting(requestResult -> requestResult.coordinates().name())
                .containsExactly("first");
    }

    @Test
    void run_continuesWhenAListenerThrows() {
        // given — a reporting bug must not turn a green suite red
        var events = new ArrayList<RunEvent>();
        RunListener throwing = event -> {
            throw new IllegalStateException("listener is broken");
        };

        // when
        var result = brat().run(
                        suite(request("r", "${env.baseUrl}/a")),
                        environment,
                        List.of(throwing, events::add),
                        new StubRunControl());

        // then
        assertThat(result.requestResults()).hasSize(1);
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);
    }

    @Test
    void run_emitsRunFinishedEvenWhenTheRunFailsFatally() {
        // given — a secrets source naming a type no factory was registered for is structural, so it
        // propagates rather than becoming a request result
        var events = new ArrayList<RunEvent>();
        var broken = new Environment(
                Map.of(),
                Map.of(),
                new SecretsProviderConfig(Map.of(), List.of(new SecretsSource("nosuch", Map.of()))));

        // when / then — the exception reaches the caller
        var thrown = catchThrowable(() -> brat().run(
                        suite(request("r", "${env.baseUrl}/a")), broken, List.of(events::add), new StubRunControl()));
        assertThat(thrown).isInstanceOf(BratException.class);

        // and the terminal event still arrived, which is what a file-writing listener depends on
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);

        // and it says what ended the run, so a listener does not report the run as passed
        var reported = ((RunEvent.RunFinished) events.getLast()).result();
        assertThat(reported.error()).isEqualTo(thrown.getMessage());
        assertThat(reported.failed()).isTrue();
    }

    @Test
    void run_namesTheTypeOfAnUnplannedFailureThatEndedTheRun() {
        // given — a defect, not a BratException: its type is part of what the listener needs to see
        var events = new ArrayList<RunEvent>();
        var broken = new RunControl() {
            @Override
            public void cancel() {
                // never called
            }

            @Override
            public boolean isCancelled() {
                throw new IllegalStateException("boom");
            }
        };

        // when
        assertThatThrownBy(() ->
                        brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), broken))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(((RunEvent.RunFinished) events.getLast()).result().error()).isEqualTo("IllegalStateException: boom");
    }

    @Test
    void run_deliversRunFinishedEvenWhenTheRunControlKeepsThrowing() {
        // given — the runner asks the control again while assembling the final result
        var events = new ArrayList<RunEvent>();
        var broken = new RunControl() {
            @Override
            public void cancel() {
                // never called
            }

            @Override
            public boolean isCancelled() {
                throw new IllegalStateException("boom");
            }
        };

        // when
        assertThatThrownBy(() ->
                        brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), broken))
                .isInstanceOf(IllegalStateException.class);

        // then — the guarantee a file-writing listener depends on survives the control's failure
        assertThat(events).filteredOn(RunEvent.RunFinished.class::isInstance).hasSize(1);
        assertThat(((RunEvent.RunFinished) events.getLast()).result().cancelled())
                .isFalse();
    }

    @Test
    void run_recordsAnErrorThatEndedTheRun() {
        // given — an Error, not an exception: it ends the run all the same
        var events = new ArrayList<RunEvent>();
        var broken = new RunControl() {
            @Override
            public void cancel() {
                // never called
            }

            @Override
            public boolean isCancelled() {
                throw new AssertionError("broken double");
            }
        };

        // when
        assertThatThrownBy(() ->
                        brat().run(suite(request("r", "${env.baseUrl}/a")), environment, List.of(events::add), broken))
                .isInstanceOf(AssertionError.class);

        // then — a listener must not read it as a run that passed
        var reported = ((RunEvent.RunFinished) events.getLast()).result();
        assertThat(reported.error()).isEqualTo("AssertionError: broken double");
        assertThat(reported.failed()).isTrue();
    }

    @Test
    void run_reportsNoErrorForARunThatEndedOnItsOwn() {
        // when
        var result = brat().run(suite(request("r", "${env.baseUrl}/a")), environment);

        // then
        assertThat(result.error()).isNull();
    }

    // --- stubs ---

    /** Answers one func no core rule knows, so that reaching it is observable. */
    private static final class AlwaysTrueConditionResolverRule implements ConditionResolverRule {

        @Override
        public Optional<Boolean> resolve(Condition condition) {
            return "isAlwaysTrue".equals(condition.getFunc()) ? Optional.of(true) : Optional.empty();
        }

        @Override
        public String category() {
            return "test";
        }
    }

    private static final class StubRunControl implements RunControl {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final class FixedSecretsProviderFactory implements SecretsProviderFactory {

        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public String type() {
            return "fixed";
        }

        @Override
        public SecretsProvider create(Map<String, String> params) {
            return new SecretsProvider() {

                @Override
                public Optional<String> getSecret(String key) {
                    return "token".equals(key) ? Optional.of("s3cr3t") : Optional.empty();
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }
    }

    @Test
    void run_failsAtLaunchWhenABodyFileIsNotThere() {
        // given - a token-free path is knowable before anything runs
        var definition =
                new HttpRequestDefinition("http://url", "POST", Map.of("file", "file:/no/such/body.json"), null);
        var request = new Request("create", null, null, null, null, null, null, definition, null, null);

        // then - and it throws rather than reporting, because no run ever started
        assertThatThrownBy(() -> brat().run(suite(request), environment))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("body.json");
    }

    // ---------- run(Launch) ----------

    @Test
    void runLaunch_runsASuiteFileAgainstAnEnvironmentDirectory(@TempDir Path dir) throws IOException {
        // given
        var suite = Files.writeString(dir.resolve("orders.brat.yaml"), """
                name: orders
                requests:
                  - name: list orders
                    requestDefinition:
                      url: "${env.baseUrl}/orders"
                """);
        var dev = Files.createDirectory(dir.resolve("dev"));
        Files.writeString(dev.resolve("env.yaml"), "baseUrl: http://dev.example.com\n");
        var capturing = new CapturingHandler();

        // when
        var result = Brat.builder()
                .requestHandler(capturing)
                .build()
                .run(Launch.of(suite.toString()).withEnvironmentDirectory(dev.toString()));

        // then
        assertThat(result.requestResults()).hasSize(1);
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getUrl()).isEqualTo("http://dev.example.com/orders"));
    }

    @Test
    void runLaunch_resolvesABareBodyPathNextToTheSuiteFile(@TempDir Path dir) throws IOException {
        // given - a bare path, which only resolves because the launch told the run where the suite is
        var suite = Files.writeString(dir.resolve("orders.brat.yaml"), """
                name: orders
                requests:
                  - name: create an order
                    requestDefinition:
                      url: http://localhost/orders
                      method: POST
                      body:
                        file: bodies/create-order.json
                """);
        Files.createDirectory(dir.resolve("bodies"));
        Files.writeString(dir.resolve("bodies/create-order.json"), "{\"item\": \"widget\"}");
        var capturing = new CapturingHandler();

        // when
        Brat.builder().requestHandler(capturing).build().run(Launch.of(suite.toString()));

        // then
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getBody()).containsEntry(BODY_STRING, "{\"item\": \"widget\"}"));
    }

    @Test
    void runLaunch_failsBeforeAnyEventWhenTheLaunchCannotBeRead(@TempDir Path dir) {
        // given
        var events = new ArrayList<RunEvent>();
        var launch = Launch.of(dir.resolve("missing.brat.yaml").toString());

        // then
        assertThatThrownBy(() -> brat().run(launch, List.of(events::add), new StubRunControl()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("missing.brat.yaml");
        assertThat(events).isEmpty();
    }

    @Test
    void runLaunch_failsBeforeAnyEventWhenABareBodyFileIsNotNextToTheSuite(@TempDir Path dir) throws IOException {
        // given
        var suite = Files.writeString(dir.resolve("orders.brat.yaml"), """
                name: orders
                requests:
                  - name: create an order
                    requestDefinition:
                      url: http://localhost/orders
                      method: POST
                      body:
                        file: bodies/absent.json
                """);
        var events = new ArrayList<RunEvent>();

        // then
        assertThatThrownBy(() -> brat().run(Launch.of(suite.toString()), List.of(events::add), new StubRunControl()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("absent.json");
        assertThat(events).isEmpty();
    }

    @Test
    void runLaunch_deliversTheRunToExactlyTheGivenListeners(@TempDir Path dir) throws IOException {
        // given
        var suite = Files.writeString(dir.resolve("orders.brat.yaml"), """
                name: orders
                requests:
                  - name: list orders
                    requestDefinition:
                      url: http://localhost/orders
                """);
        var events = new ArrayList<RunEvent>();

        // when
        var result = brat().run(Launch.of(suite.toString()), List.of(events::add), new StubRunControl());

        // then
        assertThat(events).first().isInstanceOf(RunEvent.RunStarted.class);
        assertThat(events).last().isEqualTo(new RunEvent.RunFinished(result));
    }

    @Test
    void runLaunch_throwsForANullArgument() {
        // given
        var launch = Launch.of("orders.brat.yaml");
        var control = new StubRunControl();

        // then
        assertThatThrownBy(() -> brat().run((Launch) null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(null, List.of(), control)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(launch, null, control)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(launch, List.of(), null)).isInstanceOf(BratException.class);
    }

    // ---------- overrides ----------

    @Test
    void run_letsAnEnvOverrideReplaceTheEnvValue() {
        // given
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load(oneRequest("${env.baseUrl}/orders"));

        // when
        brat.run(suite, Environment.of(Map.of("baseUrl", "http://dev"), Map.of("env.baseUrl", "http://local")));

        // then
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getUrl()).isEqualTo("http://local/orders"));
    }

    @Test
    void run_letsAConstantsOverrideReplaceTheSuitesConstant() {
        // given
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load("""
                name: s
                constants:
                  path: /orders
                requests:
                  - name: r
                    requestDefinition:
                      url: "http://h${constants.path}"
                """);

        // when
        brat.run(suite, Environment.of(Map.of(), Map.of("constants.path", "/invoices")));

        // then
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getUrl()).isEqualTo("http://h/invoices"));
    }

    @Test
    void run_seedsVarsFromAnOverrideAndLetsALaterCaptureReplaceIt() {
        // given - a seed, not a pin: the capture after the first request wins
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load("""
                name: s
                requests:
                  - name: first
                    requestDefinition:
                      url: "http://h/${vars.orderId}"
                    responseActions:
                      setVars:
                        orderId: "${response.json.$.id}"
                  - name: second
                    requestDefinition:
                      url: "http://h/${vars.orderId}"
                """);

        // when
        brat.run(suite, Environment.of(Map.of(), Map.of("vars.orderId", "42")));

        // then
        assertThat(capturing.sent)
                .extracting(HttpRequestDefinition::getUrl)
                .containsExactly("http://h/42", "http://h/7");
    }

    @Test
    void run_resolvesASecretsOverrideAndMasksItInWhatIsReported() {
        // given
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load("""
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://h/x
                      headers:
                        X-Token: "${secrets.token}"
                """);

        // when
        var result = brat.run(suite, Environment.of(Map.of(), Map.of("secrets.token", "launch-s3cret")));

        // then - it reached the wire, and every reported trace of it is masked
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getHeaders()).containsEntry("X-Token", "launch-s3cret"));
        var sent = (HttpRequestDefinition) result.requestResults().getFirst().requestDefinition();
        assertThat(sent.getOutcomes().values())
                .extracting(InterpolationOutcome::reportingString)
                .noneMatch(reported -> reported.contains("launch-s3cret"))
                .anyMatch(reported -> reported.contains("***"));
    }

    @Test
    void run_readsAParamsPrefixedKeyAsThatParam() {
        // given
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load(oneRequest("http://h/${params.tenant}"));

        // when
        brat.run(suite, Environment.of(Map.of(), Map.of("params.tenant", "acme")));

        // then
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getUrl()).isEqualTo("http://h/acme"));
    }

    @Test
    void run_noLongerOffersARoutedKeyAsAParam() {
        // given - a namespace name is a reserved prefix, so this param is an override and nothing else
        var brat = Brat.builder().requestHandler(new CapturingHandler()).build();
        var suite = brat.loader().load(oneRequest("${params.env.baseUrl}/x"));

        // when
        var result = brat.run(suite, Environment.of(Map.of(), Map.of("env.baseUrl", "http://h")));

        // then
        assertThat(result.requestResults())
                .singleElement()
                .satisfies(request -> assertThat(request.status()).isInstanceOf(RequestStatus.Errored.class));
    }

    @Test
    void run_keepsAMistypedNamespaceAsAnOrdinaryParam() {
        // given - 'evn' is no namespace, so this overrides nothing (and the run warns about it)
        var capturing = new CapturingHandler();
        var brat = Brat.builder().requestHandler(capturing).build();
        var suite = brat.loader().load(oneRequest("${params.evn.baseUrl}/x"));

        // when
        brat.run(suite, Environment.of(Map.of(), Map.of("evn.baseUrl", "http://typo")));

        // then
        assertThat(capturing.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.getUrl()).isEqualTo("http://typo/x"));
    }

    /**
     * A suite of one request to {@code url}.
     *
     * @param url the request's URL, tokens and all
     * @return the suite document
     */
    private static String oneRequest(String url) {
        return "name: s\nrequests:\n  - name: r\n    requestDefinition:\n      url: \"" + url + "\"\n";
    }

    /** A handler recording every request definition it was asked to send. */
    private static final class CapturingHandler implements HttpRequestHandler {

        private final List<HttpRequestDefinition> sent = new ArrayList<>();

        @Override
        public String name() {
            return "capturing";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            sent.add(definition);
            return new HttpResponse(200, Map.of(), "{\"id\": \"7\"}");
        }
    }

    // ---------- close ----------

    @Test
    void close_closesTheHandlersItDiscoveredEvenWhenOneOfThemThrows() {
        // given
        ClosingHandler.CLOSES.set(0);
        ThrowingCloseHandler.ATTEMPTS.set(0);
        var brat = Brat.builder()
                .classLoader(closeableHandlerLoader())
                .defaultRequestHandler("http", ClosingHandler.NAME)
                .build();

        // when - and it does not throw, though one handler does
        brat.close();

        // then
        assertThat(ClosingHandler.CLOSES).hasValue(1);
        assertThat(ThrowingCloseHandler.ATTEMPTS).hasValue(1);
    }

    @Test
    void build_closesTheHandlersItCreatedWhenItFails() {
        // given - discovery creates the handler, then a default reporter nobody registered fails the build
        ClosingHandler.CLOSES.set(0);
        var builder = Brat.builder()
                .classLoader(closeableHandlerLoader())
                .defaultRequestHandler("http", ClosingHandler.NAME)
                .defaultReporters(List.of("nobody"));

        // when
        assertThatThrownBy(builder::build).isInstanceOf(BratException.class);

        // then - no runner was returned, so nothing else could ever close it
        assertThat(ClosingHandler.CLOSES).hasValue(1);
    }

    @Test
    void close_leavesAHandlerAddedOnTheBuilderOpen() {
        // given
        var closed = new AtomicBoolean();
        var wired = new CloseableHandler(closed);
        var brat = Brat.builder().requestHandler(wired).build();

        // when
        brat.close();

        // then - whoever created it closes it
        assertThat(closed).isFalse();
    }

    @Test
    void close_passesOverADiscoveredHandlerWithNothingToClose() {
        // given - this fixture's handler is not AutoCloseable
        var root = BratTest.class.getClassLoader().getResource("handler-plugin-fixture/");
        var brat = Brat.builder()
                .classLoader(new URLClassLoader(new URL[] {root}, BratTest.class.getClassLoader()))
                .defaultRequestHandler("http", "discovered-handler")
                .build();

        // when
        brat.close();

        // then - closed all the same: it runs nothing more
        assertThatThrownBy(() -> brat.run(suite(request("r", "http://h/x")), environment))
                .isInstanceOf(BratException.class);
    }

    @Test
    void close_doesNothingTheSecondTime() {
        // given
        ClosingHandler.CLOSES.set(0);
        var brat = Brat.builder()
                .classLoader(closeableHandlerLoader())
                .defaultRequestHandler("http", ClosingHandler.NAME)
                .build();
        brat.close();

        // when
        brat.close();

        // then
        assertThat(ClosingHandler.CLOSES).hasValue(1);
    }

    @Test
    void run_throwsOnceTheRunnerIsClosed(@TempDir Path dir) throws IOException {
        // given
        var brat = brat();
        var file = Files.writeString(dir.resolve("s.brat.yaml"), oneRequest("http://h/x"));
        brat.close();

        // then
        assertThatThrownBy(() -> brat.run(suite(request("r", "http://h/x")), environment))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat.run(Launch.of(file.toString()))).isInstanceOf(BratException.class);
    }

    /**
     * A classloader declaring {@link ClosingHandler} and {@link ThrowingCloseHandler} as plugins.
     *
     * @return the loader
     */
    private static ClassLoader closeableHandlerLoader() {
        var root = BratTest.class.getClassLoader().getResource("closeable-handler-plugin-fixture/");
        return new URLClassLoader(new URL[] {root}, BratTest.class.getClassLoader());
    }

    /** A discoverable HTTP handler counting how often it is closed. */
    public static final class ClosingHandler implements HttpRequestHandler, AutoCloseable {

        /** Its name. */
        static final String NAME = "closing";

        /** How often any instance was closed. */
        static final AtomicInteger CLOSES = new AtomicInteger();

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            return new HttpResponse(200, Map.of(), "{}");
        }

        @Override
        public void close() {
            CLOSES.incrementAndGet();
        }
    }

    /** A discoverable HTTP handler whose close always fails. */
    public static final class ThrowingCloseHandler implements HttpRequestHandler, AutoCloseable {

        /** How often any instance was asked to close. */
        static final AtomicInteger ATTEMPTS = new AtomicInteger();

        @Override
        public String name() {
            return "throwing-close";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            return new HttpResponse(200, Map.of(), "{}");
        }

        @Override
        public void close() {
            ATTEMPTS.incrementAndGet();
            throw new IllegalStateException("cannot close");
        }
    }

    /** A handler that records being closed, for adding on the builder. */
    private static final class CloseableHandler implements HttpRequestHandler, AutoCloseable {

        private final AtomicBoolean closed;

        private CloseableHandler(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public String name() {
            return "wired";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            return new HttpResponse(200, Map.of(), "{}");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}

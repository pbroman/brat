package dev.pbroman.brat.core.runner;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
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
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BratTest {

    private final HttpRequestHandler handler = definition -> new HttpResponse(200, Map.of(), "{\"id\": \"7\"}");

    private final Environment environment = Environment.of(Map.of("baseUrl", "http://localhost:8080"), Map.of());

    private static Request request(String name, String url) {
        return new Request(
                name,
                null,
                null,
                null,
                null,
                null,
                new HttpRequestDefinition(url, "GET", null, null, null, null),
                null,
                null);
    }

    private static TestSuite suite(Request... requests) {
        return new TestSuite("suite", null, null, null, null, null, null, null, null, List.of(requests), null);
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

    // --- wiring ---

    @Test
    void build_throwsWithoutARequestHandler() {
        // when / then — a runner that cannot perform a request is not a runner
        assertThatThrownBy(() -> Brat.builder().build()).isInstanceOf(BratException.class);
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
                new Condition("isAlwaysTrue", "a", null),
                null,
                null,
                new HttpRequestDefinition("${env.baseUrl}/a", "GET", null, null, null, null),
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
    void run_rejectsASuiteDeclaringSubSuites() {
        // given — the tree walk and its inheritance are a later phase
        var nested =
                new TestSuite("outer", null, null, null, null, null, null, null, null, List.of(), List.of(suite()));

        // when / then — rejected rather than half-run
        assertThatThrownBy(() -> brat().run(nested, environment))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("subSuites");
    }

    @Test
    void run_throwsForANullArgument() {
        // when / then
        assertThatThrownBy(() -> brat().run(null, environment)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat().run(suite(), null)).isInstanceOf(BratException.class);
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
                new HttpRequestDefinition("${env.baseUrl}/a", "GET", null, null, null, null),
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
        assertThatThrownBy(() -> brat().run(
                                suite(request("r", "${env.baseUrl}/a")),
                                broken,
                                List.of(events::add),
                                new StubRunControl()))
                .isInstanceOf(BratException.class);

        // and the terminal event still arrived, which is what a file-writing listener depends on
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);
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
}

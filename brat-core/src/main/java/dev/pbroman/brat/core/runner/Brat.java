package dev.pbroman.brat.core.runner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ResponseActionsHandler;
import dev.pbroman.brat.core.interpolation.FunctionEvaluator;
import dev.pbroman.brat.core.interpolation.FunctionRegistry;
import dev.pbroman.brat.core.interpolation.InterpolationRuleDispatcher;
import dev.pbroman.brat.core.interpolation.InterpolationScanner;
import dev.pbroman.brat.core.interpolation.configdata.AssertionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.AuthInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ChainedConditionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ConditionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.FlowControlInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.HttpRequestDefinitionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.RepeatUntilInterpolator;
import dev.pbroman.brat.core.interpolation.functions.StandardFunctions;
import dev.pbroman.brat.core.interpolation.rules.ConstantsInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.EnvInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.ParamsInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.ResponseBodyInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.ResponseHeaderInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.ResponseJsonInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.ResponseStatusCodeInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.SecretsInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.VarsInterpolationRule;
import dev.pbroman.brat.core.resolver.assertion.AssertionChainResolver;
import dev.pbroman.brat.core.resolver.condition.ConditionResolverRuleDispatcher;
import dev.pbroman.brat.core.resolver.condition.rules.BooleanConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.DateConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.FormatConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.JsonConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NullConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NumberConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import dev.pbroman.brat.core.secrets.SecretsBootstrap;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.util.Constants.PATH_DELIMITER;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The composition root: assembles a runner once, then runs suites with it.
 * <p>
 * <strong>Wiring is separate from running</strong> because they take different inputs. What a runner
 * is made of — rules, functions, secrets provider factories, the request handler — is fixed for the
 * process; what a run needs is a suite and an environment. So {@code Brat.builder()} is called once
 * and {@link #run(TestSuite, Environment)} as often as wanted.
 *
 * <pre>{@code
 * var brat = Brat.builder()
 *         .requestHandler(new ApacheHttpRequestHandler())
 *         .build();
 *
 * RunResult result = brat.run(suite, Environment.of(Map.of("baseUrl", "http://localhost:8080"), Map.of()));
 * }</pre>
 *
 * <strong>Secrets are per run, not per runner.</strong> A secrets provider's own parameters are
 * interpolated against the run's namespaces, so the provider chain — and the interpolation the rest
 * of the run resolves through — is built inside {@link #run(TestSuite, Environment)}. A runner is
 * therefore safe to keep and reuse, and two runs never share a provider.
 * <p>
 * <strong>This is the batteries-included path, not the only one.</strong> The builder accepts
 * <em>additions</em> — rules, functions, factories, a handler — and no replacements for the
 * collaborators it assembles. A consumer wanting a different {@code Interpolation},
 * {@code ConditionResolver} or {@code ResponseHandler} constructs a {@code RequestProcessor} itself.
 */
@Slf4j
public final class Brat {

    private final List<InterpolationRule> coreInterpolationRules;
    private final List<InterpolationRule> extraInterpolationRules;
    private final ConditionResolver conditionResolver;
    private final FunctionEvaluator functionEvaluator;
    private final SecretsBootstrap secretsBootstrap;
    private final HttpRequestHandler requestHandler;
    private final ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator;
    private final ConfigDataInterpolator<Condition> conditionInterpolator;
    private final ConfigDataInterpolator<Assertion> assertionInterpolator;
    private final ConfigDataInterpolator<FlowControl> flowControlInterpolator;

    private Brat(Builder builder) {
        var functions = new ArrayList<>(StandardFunctions.all());
        functions.addAll(builder.functions);
        functions.addAll(PluginDiscovery.discover(BratFunction.class, builder.classLoader));

        var conditionRules = new ArrayList<>(List.of(
                new StringConditionResolverRule(),
                new NumberConditionResolverRule(),
                new BooleanConditionResolverRule(),
                new DateConditionResolverRule(),
                new FormatConditionResolverRule(),
                new JsonConditionResolverRule(),
                new NullConditionResolverRule()));
        conditionRules.addAll(builder.conditionResolverRules);
        conditionRules.addAll(PluginDiscovery.discover(ConditionResolverRule.class, builder.classLoader));

        var factories = new ArrayList<SecretsProviderFactory>(List.of(new FileSecretsProviderFactory()));
        factories.addAll(builder.secretsProviderFactories);
        factories.addAll(PluginDiscovery.discover(SecretsProviderFactory.class, builder.classLoader));

        var extras = new ArrayList<>(builder.interpolationRules);
        extras.addAll(PluginDiscovery.discover(InterpolationRule.class, builder.classLoader));

        // Only the three launch namespaces resolve a secrets provider's own parameters. Everything
        // here runs before any secret exists, so a rule needing one could never work.
        var constants = new ConstantsInterpolationRule();
        var env = new EnvInterpolationRule();
        var params = new ParamsInterpolationRule();
        var launchRules = List.<InterpolationRule>of(constants, env, params);
        this.coreInterpolationRules = List.of(
                constants,
                env,
                params,
                new VarsInterpolationRule(),
                new ResponseBodyInterpolationRule(),
                new ResponseStatusCodeInterpolationRule(),
                new ResponseHeaderInterpolationRule(),
                new ResponseJsonInterpolationRule());
        this.extraInterpolationRules = List.copyOf(extras);
        this.conditionResolver = new ConditionResolverRuleDispatcher(conditionRules);
        this.functionEvaluator = new FunctionEvaluator(new FunctionRegistry(functions));
        this.secretsBootstrap = new SecretsBootstrap(factories, launchRules);
        this.requestHandler = builder.requestHandler;
        var chainedConditionInterpolator = new ChainedConditionInterpolator();
        this.conditionInterpolator = new ConditionInterpolator();
        this.assertionInterpolator = new AssertionInterpolator(chainedConditionInterpolator);
        this.requestDefinitionInterpolator = new HttpRequestDefinitionInterpolator(new AuthInterpolator());
        this.flowControlInterpolator = new FlowControlInterpolator(new RepeatUntilInterpolator());
    }

    /**
     * Returns a builder for a runner.
     *
     * @return a new builder, carrying the core defaults and no plugins yet
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs every top-level request of {@code suite} in order, and returns what happened.
     * <p>
     * <strong>What happens, in order:</strong> the run's {@code RuntimeData} is assembled from the
     * suite's {@code constants} and the environment's {@code env} and {@code params}; the secrets
     * chain is built from the environment's configuration against those namespaces; the interpolation
     * layer is composed around it; then each request runs through a {@code RequestProcessor}.
     * <p>
     * <strong>A list, not a tree.</strong> A suite declaring {@code subSuites} is rejected rather than
     * half-run: running the top level of a tree whose requests expect cascaded {@code auth} and
     * defaults would report a green suite that never tested what the author wrote.
     * <p>
     * Nothing about a request escapes as an exception: a definition that cannot be interpolated, a
     * failed assertion and a failed capture all become data on that request's result. What throws is
     * structural — a suite that cannot be walked, a handler that is missing.
     *
     * @param suite the suite whose top-level requests to run; never {@code null}
     * @param environment the launch namespaces and secrets configuration for this run; never
     *        {@code null}
     * @return the run's record, holding one {@link dev.pbroman.brat.core.data.result.RequestResult}
     *         per request that ran, in execution order
     * @throws BratException if either argument is {@code null}, if {@code suite} declares
     *         {@code subSuites}, or if the run cannot be assembled — a secrets source naming an
     *         unregistered provider type, say
     */
    public RunResult run(TestSuite suite, Environment environment) {
        return run(suite, environment, List.of(), new NoOpRunControl());
    }

    /**
     * Runs {@code suite}, reporting events as they happen and stopping when asked.
     * <p>
     * {@code RunStarted} is delivered before any other event and {@code RunFinished} <strong>exactly
     * once</strong> — on success, on cancellation and on a fatal error alike — so a listener holding a
     * file handle always has a point at which to flush and close. A listener that throws is logged at
     * WARN and the run continues; a reporting bug must not turn a green suite red.
     * <p>
     * Cancellation is checked <strong>between requests</strong>, so an in-flight request finishes.
     *
     * @param suite the suite whose top-level requests to run; never {@code null}
     * @param environment the launch namespaces and secrets configuration for this run; never
     *        {@code null}
     * @param listeners the listeners to deliver events to, in the order they are called; never
     *        {@code null}, possibly empty
     * @param runControl the channel a listener or the caller stops the run through; never
     *        {@code null}
     * @return the run's record, with {@code cancelled} set if the run was stopped early
     * @throws BratException under the same conditions as {@link #run(TestSuite, Environment)}
     */
    public RunResult run(TestSuite suite, Environment environment, List<RunListener> listeners, RunControl runControl) {
        nonNull(suite, "The suite to run must not be null");
        nonNull(environment, "The environment to run against must not be null");
        nonNull(listeners, "The listeners must not be null");
        nonNull(runControl, "The run control must not be null");
        if (!suite.subSuites().isEmpty()) {
            throw new BratException("The suite '" + suite.name()
                    + "' declares subSuites, which this runner cannot walk. Run a suite with requests only.");
        }

        var startedAt = System.currentTimeMillis();
        emit(listeners, new RunEvent.RunStarted(Instant.now()));
        var results = new ArrayList<RequestResult>();
        RunResult result = null;
        try {
            openRun(suite, environment, listeners, runControl, results);
            result = finish(results, startedAt, runControl);
            return result;
        } finally {
            // In a finally so the terminal event survives a fatal error: a listener holding a file
            // handle has no other point at which to flush and close. The instance the caller gets is
            // the one the listener sees, so the two can never disagree.
            var reported = result == null ? finish(results, startedAt, runControl) : result;
            emit(listeners, new RunEvent.RunFinished(reported));
        }
    }

    /**
     * Builds the run's record from what has been collected so far.
     *
     * @param results the request results collected
     * @param startedAt when the run began, in milliseconds
     * @param runControl the cancellation channel
     * @return the record
     */
    private static RunResult finish(List<RequestResult> results, long startedAt, RunControl runControl) {
        return new RunResult(List.copyOf(results), System.currentTimeMillis() - startedAt, runControl.isCancelled());
    }

    /**
     * Builds the run's namespaces and secrets chain, and closes the chain when the run ends.
     *
     * @param suite the suite to run
     * @param environment the launch namespaces and secrets configuration
     * @param listeners the listeners to emit to
     * @param runControl the cancellation channel
     * @param results collects one result per request that ran, in order
     */
    private void openRun(
            TestSuite suite,
            Environment environment,
            List<RunListener> listeners,
            RunControl runControl,
            List<RequestResult> results) {
        var runtimeData = new RuntimeData(suite.constants(), environment.env(), new HashMap<>(), environment.params());
        // try-with-resources rather than a finally: a chain holding a lease or a file handle is built
        // per run and must not outlive it, and this is the form that suppresses a close failure when
        // the run itself threw, instead of replacing the failure the caller needs to see.
        try (var secretsProvider = secretsBootstrap.build(environment.secretsConfig(), runtimeData)) {
            runRequests(suite, listeners, runControl, results, runtimeData, secretsProvider);
        }
    }

    /**
     * Composes the per-run collaborators around the run's secrets chain and walks the suite's requests.
     *
     * @param suite the suite to run
     * @param listeners the listeners to emit to
     * @param runControl the cancellation channel
     * @param results collects one result per request that ran, in order
     * @param runtimeData the run's namespaces
     * @param secretsProvider the chain {@code ${secrets.…}} resolves through
     */
    private void runRequests(
            TestSuite suite,
            List<RunListener> listeners,
            RunControl runControl,
            List<RequestResult> results,
            RuntimeData runtimeData,
            SecretsProvider secretsProvider) {
        var rules = new ArrayList<>(coreInterpolationRules);
        rules.add(new SecretsInterpolationRule(secretsProvider));
        rules.addAll(extraInterpolationRules);
        var interpolation = new InterpolationScanner(new InterpolationRuleDispatcher(rules), functionEvaluator);

        var conditionEvaluator = new ConditionEvaluator(interpolation, conditionInterpolator, conditionResolver);
        var responseHandler = new ResponseActionsHandler(
                interpolation, new AssertionChainResolver(interpolation, conditionResolver, assertionInterpolator));
        Consumer<AttemptFinished> attemptListener = attempt -> emit(listeners, attempt);
        var processor = new RequestProcessor(
                interpolation,
                requestDefinitionInterpolator,
                conditionEvaluator,
                responseHandler,
                flowControlInterpolator,
                new RequestExecutor(requestHandler, conditionEvaluator, attemptListener));

        var requestNo = 0;
        for (var request : suite.requests()) {
            if (runControl.isCancelled()) {
                return;
            }
            requestNo++;
            var coordinates = new RequestCoordinates(
                    suite.name() + PATH_DELIMITER + request.name(), request.id(), request.name(), requestNo);
            emit(listeners, new RunEvent.RequestStarted(coordinates));
            var result = processor.process(request, coordinates, runtimeData);
            results.add(result);
            emit(listeners, new RunEvent.RequestFinished(result));
        }
    }

    /**
     * Delivers one event to every listener, one at a time.
     *
     * @param listeners the listeners to deliver to
     * @param event the event to deliver
     */
    private static void emit(List<RunListener> listeners, RunEvent event) {
        for (var listener : listeners) {
            try {
                listener.on(event);
            } catch (RuntimeException e) {
                log.warn(
                        "The listener {} threw on {}; the run continues",
                        listener.getClass().getName(),
                        event.getClass().getSimpleName(),
                        e);
            }
        }
    }

    /** The control a run with no caller-supplied one uses: nothing ever cancels it. */
    private static final class NoOpRunControl implements RunControl {

        @Override
        public void cancel() {
            // Nothing can observe a cancellation on a run that was handed no control.
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * Collects what a runner is made of, then assembles it.
     * <p>
     * Every {@code add} method <strong>appends</strong>, which has opposite effects on the two kinds
     * of collection. A <em>rule</em> is searched in priority order, so an added rule at equal priority
     * is consulted after the core one and must declare a higher {@code priority()} to override it. A
     * <em>function</em> or a <em>provider factory</em> is collapsed into a registry keyed by
     * {@code name()} or {@code type()}, so an added one with a key core already uses replaces it,
     * logged at WARN. Priorities 0-100 are reserved for core.
     */
    public static final class Builder {

        private final List<InterpolationRule> interpolationRules = new ArrayList<>();
        private final List<ConditionResolverRule> conditionResolverRules = new ArrayList<>();
        private final List<BratFunction> functions = new ArrayList<>();
        private final List<SecretsProviderFactory> secretsProviderFactories = new ArrayList<>();
        private HttpRequestHandler requestHandler;
        private ClassLoader classLoader = Brat.class.getClassLoader();

        /**
         * Adds an interpolation rule, consulted after the core rules at the same priority.
         *
         * @param rule the rule to add; never {@code null}
         * @return this builder
         * @throws BratException if {@code rule} is {@code null}
         */
        public Builder interpolationRule(InterpolationRule rule) {
            nonNull(rule, "The rule to add must not be null");
            interpolationRules.add(rule);
            return this;
        }

        /**
         * Adds a condition resolver rule, consulted after the core rules at the same priority.
         *
         * @param rule the rule to add; never {@code null}
         * @return this builder
         * @throws BratException if {@code rule} is {@code null}
         */
        public Builder conditionResolverRule(ConditionResolverRule rule) {
            nonNull(rule, "The rule to add must not be null");
            conditionResolverRules.add(rule);
            return this;
        }

        /**
         * Adds a function, reachable as {@code ${__name(…)}} for its own {@code name()}.
         *
         * @param function the function to add; never {@code null}. One whose name a core function
         *        already uses replaces it, logged at WARN naming both
         * @return this builder
         * @throws BratException if {@code function} is {@code null}
         */
        public Builder function(BratFunction function) {
            nonNull(function, "The function to add must not be null");
            functions.add(function);
            return this;
        }

        /**
         * Adds a secrets provider factory, reachable as a {@code type} in a secrets configuration.
         *
         * @param factory the factory to add; never {@code null}. One whose {@code type()} a core
         *        factory already uses replaces it, logged at WARN naming both
         * @return this builder
         * @throws BratException if {@code factory} is {@code null}
         */
        public Builder secretsProviderFactory(SecretsProviderFactory factory) {
            nonNull(factory, "The factory to add must not be null");
            secretsProviderFactories.add(factory);
            return this;
        }

        /**
         * Sets the handler that performs HTTP requests, replacing any previously set.
         * <p>
         * A runner performs every request through this one handler; there is no per-request selection.
         *
         * @param handler the handler to use; never {@code null}
         * @return this builder
         * @throws BratException if {@code handler} is {@code null}
         */
        public Builder requestHandler(HttpRequestHandler handler) {
            nonNull(handler, "The request handler must not be null");
            this.requestHandler = handler;
            return this;
        }

        /**
         * Sets the classloader plugins are discovered through, replacing the default.
         * <p>
         * The default is <strong>this class's own classloader</strong>, which finds a plugin jar on the
         * classpath beside {@code brat-core}. Pass a different one where that is not where the plugins
         * are — a servlet container, or an IDE plugin with its own loader.
         *
         * @param classLoader the loader to discover through; never {@code null}
         * @return this builder
         * @throws BratException if {@code classLoader} is {@code null}
         */
        public Builder classLoader(ClassLoader classLoader) {
            nonNull(classLoader, "The classloader to discover plugins through must not be null");
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Discovers plugins and assembles everything that does not depend on a run.
         * <p>
         * Discovery happens here and once: every {@link InterpolationRule},
         * {@link ConditionResolverRule}, {@link BratFunction} and {@link SecretsProviderFactory} the
         * classloader declares is appended after the core defaults and after anything added on this
         * builder. A plugin that cannot be loaded fails here, while nothing is running.
         * <p>
         * The builder may be reused afterwards; the returned runner is unaffected by later calls.
         *
         * @return a runner ready to run suites
         * @throws BratException if no request handler was set, or if a declared plugin cannot be
         *         loaded or instantiated
         */
        public Brat build() {
            if (requestHandler == null) {
                throw new BratException("A request handler is required; a runner that cannot perform a request "
                        + "has nothing to run");
            }
            return new Brat(this);
        }
    }
}

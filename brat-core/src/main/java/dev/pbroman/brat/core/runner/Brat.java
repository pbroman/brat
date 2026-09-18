package dev.pbroman.brat.core.runner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
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
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ApacheHttpRequestHandler;
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
import dev.pbroman.brat.core.interpolation.configdata.RequestDefinitionInterpolators;
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
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.util.Constants.HTTP;
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
    private final ProtocolRegistry protocolRegistry;
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
        this.protocolRegistry = protocolRegistryOf(builder);
        var chainedConditionInterpolator = new ChainedConditionInterpolator();
        this.conditionInterpolator = new ConditionInterpolator();
        this.assertionInterpolator = new AssertionInterpolator(chainedConditionInterpolator);
        this.flowControlInterpolator = new FlowControlInterpolator(new RepeatUntilInterpolator());
    }

    /**
     * Assembles what a runner knows about protocols: every registered handler, every request
     * definition interpolator, and which handler a protocol falls back to.
     * <p>
     * <strong>Three sources, in one order</strong> — core's own, then what the builder was given,
     * then what the classloader declares. Later wins where two carry the same key, because that is
     * the order of increasing specificity: core ships defaults, an application wires its baseline,
     * and an end user who can do neither drops in a jar. Core contributes an interpolator (for
     * {@code HttpRequestDefinition}) and <strong>no handler</strong>: a handler owns a connection
     * pool, so the caller creates it and the caller closes it.
     * <p>
     * <strong>The built-in default is seeded here, and only when it can be honoured.</strong> If
     * nothing was configured as the default for {@code http} and a handler named
     * {@link dev.pbroman.brat.core.handler.ApacheHttpRequestHandler#NAME} is registered for it, that
     * one becomes the default — so adding a second HTTP handler, by wiring or by a plugin jar
     * appearing on the classpath, never silently changes which client an existing suite uses. Where
     * that handler is not registered, nothing is seeded and the registry's own ladder decides.
     *
     * @param builder the builder holding what was registered
     * @return the registry, with every inconsistency it can see already rejected
     * @throws BratException for anything {@link ProtocolRegistry} rejects at construction — a
     *         protocol whose definition type has no interpolator, two handlers for one protocol
     *         disagreeing about that type, a configured default naming a handler nobody registered
     */
    private static ProtocolRegistry protocolRegistryOf(Builder builder) {
        var interpolators = new ArrayList<RequestDefinitionInterpolator<?>>();
        interpolators.add(new HttpRequestDefinitionInterpolator(new AuthInterpolator()));
        interpolators.addAll(builder.requestDefinitionInterpolators);
        for (RequestDefinitionInterpolator<?> discovered :
                PluginDiscovery.discover(RequestDefinitionInterpolator.class, builder.classLoader)) {
            interpolators.add(discovered);
        }

        var handlers = new ArrayList<>(builder.requestHandlers);
        for (RequestHandler<?, ?> discovered : PluginDiscovery.discover(RequestHandler.class, builder.classLoader)) {
            handlers.add(discovered);
        }

        var defaults = new LinkedHashMap<>(builder.defaultRequestHandlers);
        if (!defaults.containsKey(HTTP) && registers(handlers, HTTP, ApacheHttpRequestHandler.NAME)) {
            defaults.put(HTTP, ApacheHttpRequestHandler.NAME);
        }
        return new ProtocolRegistry(handlers, defaults, new RequestDefinitionInterpolators(interpolators));
    }

    /**
     * Whether one of {@code handlers} answers to a {@code (protocol, name)} pair.
     *
     * @param handlers the handlers about to be registered
     * @param protocol the protocol to look for
     * @param name the handler name to look for
     * @return {@code true} if one of them declares both
     */
    private static boolean registers(List<RequestHandler<?, ?>> handlers, String protocol, String name) {
        return handlers.stream()
                .anyMatch(handler -> protocol.equals(handler.protocol()) && name.equals(handler.name()));
    }

    /**
     * Which handler each protocol should use for {@code request}, by what the suite tree declares.
     * <p>
     * <strong>Merged per key, not replaced wholesale</strong>: a request overriding {@code http}
     * must not drop an {@code ftp} entry it inherited, which is also why this is a map rather than a
     * single name. The request's own entries win over the suite's.
     * <p>
     * One level today, because this runner walks a list. The tree walk generalises the same merge
     * over every ancestor, and nothing below this method has to change when it does — which is why
     * selection is resolved out here rather than inside the request processor.
     *
     * @param suite the suite being run
     * @param request the request about to run
     * @return the effective protocol-to-handler-name map; never {@code null}, possibly empty
     */
    private static Map<String, String> effectiveHandlerNames(TestSuite suite, Request request) {
        var names = new HashMap<>(suite.requestHandlers());
        names.putAll(request.requestHandlers());
        return names;
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
     *         {@code subSuites}, if a body file the suite names with a token-free path does not
     *         exist, or if the run cannot be assembled — a secrets source naming an unregistered
     *         provider type, say
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
        // Before the first event: a body file that is not there is a launch failure, not a run that
        // started and then went wrong.
        BodyFileChecks.check(suite, environment.suiteLocation());

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
        var runtimeData = new RuntimeData(
                suite.constants(),
                environment.env(),
                new HashMap<>(),
                environment.params(),
                environment.suiteLocation());
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
                protocolRegistry.interpolators(),
                conditionEvaluator,
                responseHandler,
                flowControlInterpolator,
                new RequestExecutor(conditionEvaluator, attemptListener));

        var requestNo = 0;
        for (var request : suite.requests()) {
            if (runControl.isCancelled()) {
                return;
            }
            requestNo++;
            var coordinates = new RequestCoordinates(
                    suite.name() + PATH_DELIMITER + request.name(), request.id(), request.name(), requestNo);
            emit(listeners, new RunEvent.RequestStarted(coordinates));
            var handler = protocolRegistry.resolve(request.requestDefinition(), effectiveHandlerNames(suite, request));
            var result = processor.process(request, coordinates, runtimeData, handler);
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
        private final List<RequestHandler<?, ?>> requestHandlers = new ArrayList<>();
        private final List<RequestDefinitionInterpolator<?>> requestDefinitionInterpolators = new ArrayList<>();
        private final Map<String, String> defaultRequestHandlers = new LinkedHashMap<>();
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
         * Adds a handler that can perform requests, selectable by its own {@code name()}.
         * <p>
         * <strong>Adds rather than replaces.</strong> Several handlers for one protocol is the
         * ordinary case — a proxied, an mTLS and a plain client differ in configuration, not in
         * implementation — and a suite says which it wants. One whose {@code (protocol, name)} pair
         * another registration already used replaces that one, logged at WARN naming both.
         *
         * @param handler the handler to add; never {@code null}
         * @return this builder
         * @throws BratException if {@code handler} is {@code null}
         */
        public Builder requestHandler(RequestHandler<?, ?> handler) {
            nonNull(handler, "The request handler must not be null");
            requestHandlers.add(handler);
            return this;
        }

        /**
         * Adds the interpolator for one request definition type, without which a protocol's requests
         * could be authored and never resolved.
         * <p>
         * A protocol needs one: its handler names a {@code definitionType()}, and a runner whose
         * registered protocol has no interpolator for that type fails to build rather than leaving
         * {@code ${...}} tokens in a request that then goes out on the wire. One for a type another
         * registration already covered replaces it, logged at WARN.
         *
         * @param interpolator the interpolator to add; never {@code null}
         * @return this builder
         * @throws BratException if {@code interpolator} is {@code null}
         */
        public Builder requestDefinitionInterpolator(RequestDefinitionInterpolator<?> interpolator) {
            nonNull(interpolator, "The request definition interpolator must not be null");
            requestDefinitionInterpolators.add(interpolator);
            return this;
        }

        /**
         * Sets which handler a protocol uses when a suite names none, replacing any previously set
         * for that protocol.
         * <p>
         * Needed only where several handlers are registered for one protocol: with a single one it is
         * used anyway, and core seeds {@code http} with its own handler when that one is registered.
         * <strong>Never list order</strong> — plugin discovery order is unspecified, so a default has
         * to be named by somebody.
         *
         * @param protocol the protocol this default applies to; never {@code null} or blank
         * @param name the {@code name()} of the handler to use; never {@code null} or blank, and it
         *        must be registered by the time {@link #build()} runs
         * @return this builder
         * @throws BratException if either argument is {@code null} or blank
         */
        public Builder defaultRequestHandler(String protocol, String name) {
            Require.nonBlank(protocol, "The protocol of a default request handler must not be blank");
            Require.nonBlank(name, "The name of a default request handler must not be blank");
            defaultRequestHandlers.put(protocol, name);
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
         * <strong>What it refuses to build</strong>, so that a wiring mistake is never discovered by a
         * request: a runner with no handler at all; a protocol whose definition type has no
         * interpolator; two handlers for one protocol disagreeing about which class an authored
         * {@code requestDefinition:} binds to; and a default naming a handler nobody registered.
         *
         * @return a runner ready to run suites
         * @throws BratException if no request handler was registered, if a declared plugin cannot be
         *         loaded or instantiated, or if the registered protocols are inconsistent in any of
         *         the ways above — each naming what is wrong and what was registered
         */
        public Brat build() {
            if (requestHandlers.isEmpty()) {
                throw new BratException("A request handler is required; a runner that cannot perform a request "
                        + "has nothing to run");
            }
            return new Brat(this);
        }
    }
}

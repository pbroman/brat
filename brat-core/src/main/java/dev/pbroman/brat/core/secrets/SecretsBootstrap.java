package dev.pbroman.brat.core.secrets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationHandler;
import dev.pbroman.brat.core.interpolation.InterpolationRuleDispatcher;
import dev.pbroman.brat.core.interpolation.rules.SecretsInterpolationRule;
import dev.pbroman.brat.core.tools.InterpolationTools;
import tools.jackson.core.JacksonException;

import static dev.pbroman.brat.core.util.ExceptionUtils.bratExceptionOnAnyNull;
import static dev.pbroman.brat.core.util.ExceptionUtils.bratExceptionOnNull;
import static dev.pbroman.brat.core.util.JacksonUtils.locationOf;

/**
 * Builds the chain of secrets providers described by a {@link SecretsProviderConfig}.
 * <p>
 * Sources are walked in order, each yielding one provider. The parameters a source's provider is
 * built from are interpolated immediately before it is created, against a chain holding only the
 * providers built before it — so a provider's configuration can resolve a secret from an earlier
 * source, but never from itself or from a later one. That restriction is structural rather than
 * checked: the earlier providers are simply the only ones that exist at that point, and a
 * configuration reaching further ahead fails as an unresolvable secret.
 * <p>
 * The environment-variable provider is the exception, and is available for those lookups wherever
 * it sits, because it is the only provider that needs no credentials of its own and so cannot take
 * part in a cycle. For ordinary lookups it stays last in the chain.
 * <p>
 * Resolved parameter values may be secrets and do not pass through the masking that interpolated
 * suite configuration gets, so they appear in no log line and no exception message from here.
 */
public final class SecretsBootstrap {

    /**
     * The provider type of the environment-variable provider.
     */
    public static final String SYSENV_TYPE = "sysenv";

    /**
     * The parameter setting the environment-variable provider's prefix.
     */
    public static final String PREFIX_PARAM = "prefix";

    private final List<InterpolationRule> bootstrapRules;
    private final InterpolationTools tools;
    private final UnaryOperator<String> envLookup;
    private final Map<String, SecretsProviderFactory> type2factoryMap = new HashMap<>();

    /**
     * Constructs a bootstrap over the factories and interpolation rules a run has available.
     *
     * @param factories the factories to create providers with, one per type; may be empty, which
     *        only builds chains needing no sources
     * @param bootstrapRules the interpolation rules provider parameters are resolved with,
     *        typically the {@code constants}, {@code env} and {@code params} rules; the rule
     *        resolving {@code ${secrets.…}} is supplied per step and must not be among them
     * @param tools the {@link InterpolationTools}
     * @throws BratException if any argument is {@code null}, if {@code factories} or
     *         {@code bootstrapRules} holds a {@code null} element, if two factories report the same
     *         {@link SecretsProviderFactory#type()}, or if {@code bootstrapRules} holds a rule
     *         resolving secrets
     */
    public SecretsBootstrap(List<SecretsProviderFactory> factories,
                            List<InterpolationRule> bootstrapRules,
                            InterpolationTools tools) {
        this(factories, bootstrapRules, tools, System::getenv);
    }

    SecretsBootstrap(List<SecretsProviderFactory> factories,
                     List<InterpolationRule> bootstrapRules,
                     InterpolationTools tools,
                     UnaryOperator<String> envLookup) {
        bratExceptionOnAnyNull(factories, "factories or any of its values may not be null");
        bratExceptionOnAnyNull(bootstrapRules, "bootstrapRules or any of its values may not be null");
        bratExceptionOnNull(tools, "tools may not be null");
        if (bootstrapRules.stream().anyMatch(c -> c instanceof SecretsInterpolationRule)) {
            throw new BratException("The bootstrapRules may not contain the SecretsInterpolationRule.");
        }
        for (SecretsProviderFactory factory : factories) {
            if (type2factoryMap.put(factory.type(), factory) != null) {
                throw new BratException("Multiple factories found for type: " + factory.type());
            }
        }
        this.bootstrapRules = bootstrapRules;
        this.tools = tools;
        this.envLookup = envLookup;
    }

    /**
     * Builds the provider chain {@code config} describes.
     * <p>
     * Each source yields its own provider, in list order, so two sources of the same type are two
     * independent providers and the earlier one wins a key both define. The environment-variable
     * provider is appended last, whether or not {@code config} configures {@code sysenv}: with a
     * {@code prefix} parameter it uses that prefix, with an explicitly empty one it uses none, and
     * with no parameters at all it uses {@link EnvVarSecretsProvider#DEFAULT_PREFIX}. Its own
     * parameters resolve without any secret being available, since nothing precedes it.
     * <p>
     * A type's parameters are interpolated once, when the first source of that type is reached, and
     * the result is reused by every later source of the same type — so they resolve to one value per
     * run regardless of where they are used. Source parameters are used exactly as given and are
     * never interpolated.
     * <p>
     * What a factory receives is the resolved parameters configured for the source's type, overlaid
     * with that source's own parameters, so a source can override what its type sets. A type with no
     * parameters configured leaves the source's own alone.
     *
     * @param config the provider parameters and ordered sources to build from
     * @param runtimeData the namespaces provider parameters are interpolated against
     * @return the chain, in source order and with the environment-variable provider last; a
     *         configuration with no sources yields a chain of that provider alone
     * @throws BratException if either argument is {@code null}, if a source names a type no factory
     *         was registered for, if a provider parameter references a secret no
     *         already-built provider has, or if a factory fails to create its provider; whatever
     *         had already been built is closed before the failure propagates, so a chain that fails
     *         halfway leaks nothing
     */
    public CompositeSecretsProvider build(SecretsProviderConfig config, RuntimeData runtimeData) {
        bratExceptionOnNull(config, "The config may not be null");
        bratExceptionOnNull(runtimeData, "The runtimeData may not be null");
        var sourceTypesNotAvailable = config.sources().stream()
                .map(SecretsSource::type).filter(type -> !type2factoryMap.containsKey(type)).toList();
        if (!sourceTypesNotAvailable.isEmpty()) {
            throw new BratException("The following sources have no factory registered for their provider type: "
                    + sourceTypesNotAvailable);
        }
        var providers = new ArrayList<SecretsProvider>();
        var sysenvProvider = createSysenvSecretsProvider(config);
        Map<String, Map<String, String>> type2interpolatedParams = new HashMap<>();
        buildProviders(config, runtimeData, providers, sysenvProvider, type2interpolatedParams);
        providers.add(sysenvProvider);
        return new CompositeSecretsProvider(providers);
    }

    private EnvVarSecretsProvider createSysenvSecretsProvider(SecretsProviderConfig config) {
        var prefix = config.paramsFor(SYSENV_TYPE).get(PREFIX_PARAM);
        return new EnvVarSecretsProvider(
                Objects.requireNonNullElse(prefix, EnvVarSecretsProvider.DEFAULT_PREFIX),
                envLookup);
    }

    private void buildProviders(SecretsProviderConfig config,
                                RuntimeData runtimeData,
                                ArrayList<SecretsProvider> providers,
                                SecretsProvider sysenvProvider,
                                Map<String, Map<String, String>> type2interpolatedParams) {
        for (SecretsSource source : config.sources()) {
            if (!type2interpolatedParams.containsKey(source.type())) {
                var secretsInterpolationRule = createSecretsInterpolationRule(providers, sysenvProvider);
                try {
                    var interpolatedParams = interpolateParams(config.paramsFor(source.type()), secretsInterpolationRule, runtimeData);
                    type2interpolatedParams.put(source.type(), interpolatedParams);
                } catch (BratException e) {
                    closeAndThrow(providers, e.getMessage());
                }
            }
            var factoryParams = new HashMap<>(type2interpolatedParams.get(source.type()));
            factoryParams.putAll(source.params());
            try {
                providers.add(type2factoryMap.get(source.type()).create(factoryParams));
            } catch (Exception e) {
                var detail = switch (e) {
                    case JacksonException jackson -> locationOf(jackson);
                    case BratException brat -> ": " + brat.getMessage();
                    default -> " with " + e.getClass().getSimpleName();
                };
                closeAndThrow(providers, "The SecretsProviderFactory for type '" + source.type()
                        + "' failed" + detail);
            }
        }
    }

    private SecretsInterpolationRule createSecretsInterpolationRule(ArrayList<SecretsProvider> providers,
                                                                    SecretsProvider sysenvProvider) {
        var currentProviders = new ArrayList<>(providers);
        currentProviders.add(sysenvProvider);
        return new SecretsInterpolationRule(new CompositeSecretsProvider(currentProviders), tools);
    }

    private Map<String, String> interpolateParams(Map<String, String> params,
                                                  InterpolationRule secretsInterpolationRule,
                                                  RuntimeData runtimeData) {
        var interpolated = new HashMap<>(params);
        for (Map.Entry<String, String> param : interpolated.entrySet()) {
            var outcome = createInterpolationHandler(secretsInterpolationRule).outcome(param.getValue(), runtimeData);
            param.setValue(outcome.asString());
        }
        return interpolated;
    }

    private InterpolationHandler createInterpolationHandler(InterpolationRule secretsInterpolationRule) {
        var rules = new ArrayList<>(bootstrapRules);
        rules.add(secretsInterpolationRule);
        return new InterpolationHandler(new InterpolationRuleDispatcher(rules), tools);
    }

    private void closeAndThrow(ArrayList<SecretsProvider> providers, String message) {
        providers.forEach(SecretsProvider::close);
        throw new BratException(message);
    }

}

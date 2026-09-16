package dev.pbroman.brat.core.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.util.Require;

/**
 * Everything the launch supplies for one environment: the {@code env} and {@code params} namespaces,
 * and how to build the secrets chain.
 * <p>
 * <strong>Not {@code RuntimeData}.</strong> That type mixes launch input with values written during
 * the run and an execution cursor; handing one in would make a caller construct the runner's
 * bookkeeping. It also cannot be built by a caller, since {@code constants} come from the suite —
 * which is why the runner assembles it inside {@code run(…)} from a suite and one of these.
 * <p>
 * <strong>The secrets configuration belongs here rather than to the runner</strong>, because it is
 * per-environment: pointing a run at staging changes both {@code env.baseUrl} and which vault the
 * secrets come from.
 *
 * @param env the {@code env} namespace; never {@code null}, possibly empty. Copied and
 *        unmodifiable, so a caller cannot change an environment a run is using
 * @param params the {@code params} namespace, typically launch flags; never {@code null}, possibly
 *        empty
 * @param secretsConfig the provider parameters and ordered sources the secrets chain is built from;
 *        never {@code null} — an environment with no secrets passes an empty configuration, which
 *        still yields the environment-variable provider
 * @param suiteLocation where the suite document being run was loaded from — the full location with
 *        its prefix, as {@code ResourceReader} spells one ({@code classpath:suites/orders.yaml},
 *        {@code file:/srv/suites/orders.yaml}) — or {@code null} when it came from no location, a
 *        document held in memory being the case that has none. It is what a bare body-file path
 *        resolves against, and it sits here rather than on the suite because a suite is authored
 *        content while its location is something the launch knows
 */
public record Environment(
        Map<String, Object> env,
        Map<String, Object> params,
        SecretsProviderConfig secretsConfig,
        String suiteLocation) {

    /**
     * Constructs an environment.
     *
     * @param env the {@code env} namespace
     * @param params the {@code params} namespace
     * @param secretsConfig the secrets provider configuration
     * @param suiteLocation where the suite was loaded from, or {@code null}
     * @throws BratException if {@code env}, {@code params} or {@code secretsConfig} is {@code null}.
     *         A {@code null} {@code suiteLocation} is legal — it means the suite came from nowhere a
     *         relative path could be resolved against, which only fails if one is then written
     */
    public Environment {
        Require.nonNull(env, "The env must not be null");
        Require.nonNull(params, "The params must not be null");
        Require.nonNull(secretsConfig, "The secretsConfig must not be null");
        env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * Equivalent to the canonical constructor with {@code suiteLocation} defaulted to {@code null}.
     *
     * @param env the {@code env} namespace
     * @param params the {@code params} namespace
     * @param secretsConfig the secrets provider configuration
     * @throws BratException if any argument is {@code null}
     */
    public Environment(Map<String, Object> env, Map<String, Object> params, SecretsProviderConfig secretsConfig) {
        this(env, params, secretsConfig, null);
    }

    /**
     * An environment with the given namespaces and no configured secrets sources.
     * <p>
     * The common case: a suite reaching a local server needs {@code baseUrl} and nothing else. The
     * environment-variable provider is still available, since the chain always appends it.
     *
     * @param env the {@code env} namespace; never {@code null}
     * @param params the {@code params} namespace; never {@code null}
     * @return an environment carrying an empty secrets configuration
     * @throws BratException if either argument is {@code null}
     */
    public static Environment of(Map<String, Object> env, Map<String, Object> params) {
        Require.nonNull(env, "The env must not be null");
        Require.nonNull(params, "The params must not be null");
        return new Environment(env, params, SecretsProviderConfig.empty(), null);
    }

    /**
     * A copy of this environment carrying {@code suiteLocation}.
     * <p>
     * The launch knows where the suite came from, and typically builds the environment before
     * loading it — so this is how the two are joined without threading the location through every
     * factory.
     *
     * @param suiteLocation where the suite was loaded from, prefix and all, or {@code null} for none
     * @return a new environment identical but for its suite location
     */
    public Environment withSuiteLocation(String suiteLocation) {
        return new Environment(env, params, secretsConfig, suiteLocation);
    }
}

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
 */
public record Environment(Map<String, Object> env, Map<String, Object> params, SecretsProviderConfig secretsConfig) {

    /**
     * Constructs an environment.
     *
     * @param env the {@code env} namespace
     * @param params the {@code params} namespace
     * @param secretsConfig the secrets provider configuration
     * @throws BratException if any argument is {@code null}
     */
    public Environment {
        Require.nonNull(env, "The env must not be null");
        Require.nonNull(params, "The params must not be null");
        Require.nonNull(secretsConfig, "The secretsConfig must not be null");
        env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
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
        return new Environment(env, params, SecretsProviderConfig.empty());
    }
}

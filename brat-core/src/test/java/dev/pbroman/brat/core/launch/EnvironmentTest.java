package dev.pbroman.brat.core.launch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class EnvironmentTest {

    private final SecretsProviderConfig emptyConfig = new SecretsProviderConfig(Map.of(), List.of());

    @Test
    void constructor_keepsTheNamespacesItWasGiven() {
        // when
        var environment = new Environment(Map.of("baseUrl", "http://localhost"), Map.of("n", "5"), emptyConfig);

        // then
        assertThat(environment.env()).containsEntry("baseUrl", "http://localhost");
        assertThat(environment.params()).containsEntry("n", "5");
        assertThat(environment.secretsConfig()).isSameAs(emptyConfig);
    }

    @Test
    void constructor_throwsForANullArgument() {
        // when / then — each is required; an environment with no secrets passes an empty config
        assertThatThrownBy(() -> new Environment(null, Map.of(), emptyConfig)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Environment(Map.of(), null, emptyConfig)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Environment(Map.of(), Map.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void of_suppliesAnEmptySecretsConfiguration() {
        // when
        var environment = Environment.of(Map.of("baseUrl", "http://localhost"), Map.of());

        // then — no sources configured; the environment-variable provider is still appended by the chain
        assertThat(environment.secretsConfig().sources()).isEmpty();
        assertThat(environment.secretsConfig().providerParams()).isEmpty();
        assertThat(environment.env()).containsEntry("baseUrl", "http://localhost");
    }

    @Test
    void of_throwsForANullArgument() {
        // when / then
        assertThatThrownBy(() -> Environment.of(null, Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Environment.of(Map.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void suiteLocation_defaultsToNull() {
        // then - a suite held in memory has no location, and that is legal until a bare path needs one
        assertThat(Environment.of(Map.of(), Map.of()).suiteLocation()).isNull();
        assertThat(new Environment(Map.of(), Map.of(), emptyConfig).suiteLocation())
                .isNull();
    }

    @Test
    void withSuiteLocation_copiesTheEnvironmentCarryingTheLocation() {
        // given
        var environment = new Environment(Map.of("baseUrl", "http://localhost"), Map.of("n", "5"), emptyConfig);

        // when
        var located = environment.withSuiteLocation("classpath:suites/orders.yaml");

        // then - everything else survives; the launch knows the location after building the rest
        assertThat(located.suiteLocation()).isEqualTo("classpath:suites/orders.yaml");
        assertThat(located.env()).isEqualTo(environment.env());
        assertThat(located.params()).isEqualTo(environment.params());
        assertThat(located.secretsConfig()).isEqualTo(environment.secretsConfig());
        assertThat(environment.suiteLocation()).isNull();
    }

    @Test
    void constructor_flattensANestedEnvToDottedKeys() {
        // when
        var environment = new Environment(Map.of("db", Map.of("host", "h1")), Map.of(), emptyConfig);

        // then
        assertThat(environment.env()).containsOnlyKeys("db.host").containsEntry("db.host", "h1");
    }

    @Test
    void constructor_flattensNestedParamsToDottedKeys() {
        // when
        var environment = new Environment(Map.of(), Map.of("retry", Map.of("count", "3")), emptyConfig);

        // then
        assertThat(environment.params()).containsOnlyKeys("retry.count");
    }

    @Test
    void constructor_rejectsATokenInEnvNamingTheKeyButNotTheValue() {
        // given
        Map<String, Object> env = Map.of("ordersUrl", "${env.baseUrl}/orders");

        // then
        assertThatThrownBy(() -> new Environment(env, Map.of(), emptyConfig))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.ordersUrl")
                .hasMessageNotContaining("/orders");
    }

    @Test
    void constructor_rejectsATokenInParamsNamingTheKeyButNotTheValue() {
        // given - a param is a value, not a template; it may also be an override carrying a secret
        Map<String, Object> params = Map.of("secrets.token", "abc${x}");

        // then
        assertThatThrownBy(() -> new Environment(Map.of(), params, emptyConfig))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("params.secrets.token")
                .hasMessageNotContaining("abc");
    }

    @Test
    void constructor_rejectsANestedEnvKeyCollidingWithADottedOne() {
        // given
        var env = new LinkedHashMap<String, Object>();
        env.put("db", Map.of("host", "h1"));
        env.put("db.host", "h2");

        // then
        assertThatThrownBy(() -> new Environment(env, Map.of(), emptyConfig))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.db.host");
    }

    @Test
    void of_flattensAndChecksAsTheConstructorDoes() {
        // then
        assertThat(Environment.of(Map.of("db", Map.of("host", "h1")), Map.of()).env())
                .containsOnlyKeys("db.host");
        assertThatThrownBy(() -> Environment.of(Map.of("a", "${b}"), Map.of())).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_rejectsAParamThatCannotBeRouted() {
        // then - a launch that cannot run fails before it runs
        assertThatThrownBy(() -> Environment.of(Map.of(), Map.of("responseVars.status", "200")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("responseVars.status");
    }

    @Test
    void overrides_routesThisEnvironmentsParams() {
        // given
        var environment = Environment.of(Map.of("baseUrl", "http://a"), Map.of("env.baseUrl", "http://b", "wait", "5"));

        // when
        var result = environment.overrides();

        // then
        assertThat(result.env()).containsOnly(entry("baseUrl", "http://b"));
        assertThat(result.params()).containsOnly(entry("wait", "5"));
    }
}

package dev.pbroman.brat.core.runner;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}

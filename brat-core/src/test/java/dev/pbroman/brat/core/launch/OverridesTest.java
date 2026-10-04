package dev.pbroman.brat.core.launch;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class OverridesTest {

    @Test
    void of_routesEachOverridableNamespaceUnderTheRestOfTheKey() {
        // given
        var params = Map.<String, Object>of(
                "constants.currency", "USD",
                "env.baseUrl", "http://localhost:9090",
                "vars.orderId", "42",
                "secrets.apiKey", "s3cret");

        // when
        var result = Overrides.of(params);

        // then
        assertThat(result.constants()).containsOnly(entry("currency", "USD"));
        assertThat(result.env()).containsOnly(entry("baseUrl", "http://localhost:9090"));
        assertThat(result.vars()).containsOnly(entry("orderId", "42"));
        assertThat(result.secrets()).containsOnly(entry("apiKey", "s3cret"));
        assertThat(result.params()).isEmpty();
        assertThat(result.unrecognised()).isEmpty();
    }

    @Test
    void of_keepsEveryDotAfterTheFirstInTheKey() {
        // when
        var result = Overrides.of(Map.of("env.db.host", "db.local"));

        // then - the namespaces are flat, so this overrides the key 'db.host'
        assertThat(result.env()).containsOnly(entry("db.host", "db.local"));
    }

    @Test
    void of_keepsAKeyWithoutADotAsAParam() {
        // when
        var result = Overrides.of(Map.of("wait", "500"));

        // then
        assertThat(result.params()).containsOnly(entry("wait", "500"));
        assertThat(result.unrecognised()).isEmpty();
    }

    @Test
    void of_readsAParamsPrefixedKeyAsThatParam() {
        // when
        var result = Overrides.of(Map.of("params.wait", "500"));

        // then
        assertThat(result.params()).containsOnly(entry("wait", "500"));
        assertThat(result.unrecognised()).isEmpty();
    }

    @Test
    void of_keepsAKeyWhoseFirstPartIsNoNamespaceAsAParamAndListsIt() {
        // given - a typo, which would otherwise override nothing in silence
        var params = new LinkedHashMap<String, Object>();
        params.put("evn.baseUrl", "http://x");
        params.put("tenant.id", "7");
        params.put("wait", "500");

        // when
        var result = Overrides.of(params);

        // then
        assertThat(result.params())
                .containsOnly(entry("evn.baseUrl", "http://x"), entry("tenant.id", "7"), entry("wait", "500"));
        assertThat(result.unrecognised()).containsExactly("evn.baseUrl", "tenant.id");
        assertThat(result.env()).isEmpty();
    }

    @Test
    void of_keepsValuesUnchangedButHandsSecretsOutAsText() {
        // when
        var result = Overrides.of(Map.of("env.port", 8080, "secrets.pin", 1234, "retries", 3));

        // then
        assertThat(result.env()).containsOnly(entry("port", 8080));
        assertThat(result.params()).containsOnly(entry("retries", 3));
        assertThat(result.secrets()).containsOnly(entry("pin", "1234"));
    }

    @Test
    void of_dropsASecretWithNoValue() {
        // given
        var params = new HashMap<String, Object>();
        params.put("secrets.apiKey", null);
        params.put("env.baseUrl", null);

        // when
        var result = Overrides.of(params);

        // then - a secret with no value is absent; elsewhere the null is routed like any value
        assertThat(result.secrets()).isEmpty();
        assertThat(result.env()).containsEntry("baseUrl", null);
    }

    @Test
    void of_rejectsAResponseVarsOverrideNamingTheKeyButNotTheValue() {
        // then - replaced by every response, so an override could never hold
        assertThatThrownBy(() -> Overrides.of(Map.of("responseVars.status", "s3cret")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("responseVars.status")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void of_rejectsANamespaceWithNothingAfterItsDot() {
        // then
        assertThatThrownBy(() -> Overrides.of(Map.of("env.", "x")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.");
    }

    @Test
    void of_rejectsTwoKeysNamingOneParam() {
        // given
        var params = Map.<String, Object>of("wait", "500", "params.wait", "600");

        // then
        assertThatThrownBy(() -> Overrides.of(params))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("wait");
    }

    @Test
    void of_returnsEmptyRoutingForNoParams() {
        // when
        var result = Overrides.of(Map.of());

        // then
        assertThat(result.params()).isEmpty();
        assertThat(result.constants()).isEmpty();
        assertThat(result.env()).isEmpty();
        assertThat(result.vars()).isEmpty();
        assertThat(result.secrets()).isEmpty();
        assertThat(result.unrecognised()).isEmpty();
    }

    @Test
    void of_handsOutUnmodifiableCollectionsAndLeavesItsInputAlone() {
        // given
        var params = new HashMap<String, Object>(Map.of("env.a", "1", "b.c", "2"));

        // when
        var result = Overrides.of(params);

        // then
        assertThat(params).containsOnly(entry("env.a", "1"), entry("b.c", "2"));
        assertThatThrownBy(() -> result.env().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.params().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.secrets().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unrecognised().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_copiesItsComponentsAndThrowsForANullOne() {
        // given
        var env = new HashMap<String, Object>(Map.of("a", "1"));
        Map<String, Object> none = Map.of();

        // when
        var overrides = new Overrides(none, none, env, none, Map.of(), List.of());
        env.put("b", "2");

        // then
        assertThat(overrides.env()).containsOnly(entry("a", "1"));
        assertThatThrownBy(() -> new Overrides(null, none, none, none, Map.of(), List.of()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Overrides(none, null, none, none, Map.of(), List.of()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Overrides(none, none, null, none, Map.of(), List.of()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Overrides(none, none, none, null, Map.of(), List.of()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Overrides(none, none, none, none, null, List.of()))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new Overrides(none, none, none, none, Map.of(), null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void of_throwsForNullParams() {
        // then
        assertThatThrownBy(() -> Overrides.of(null)).isInstanceOf(BratException.class);
    }
}

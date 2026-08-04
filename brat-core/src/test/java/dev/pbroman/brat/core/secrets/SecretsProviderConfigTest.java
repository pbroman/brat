package dev.pbroman.brat.core.secrets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class SecretsProviderConfigTest {

    private static final SecretsSource FILE_SOURCE = new SecretsSource("file", Map.of("location", "one.yaml"));

    @Test
    void constructor_keepsProviderParamsAndSources() {
        // when
        var underTest = new SecretsProviderConfig(
                Map.of("vault", Map.of("address", "https://vault.example.com")), List.of(FILE_SOURCE));

        // then
        assertThat(underTest.providerParams()).containsOnlyKeys("vault");
        assertThat(underTest.sources()).containsExactly(FILE_SOURCE);
    }

    @Test
    void constructor_acceptsNoParamsAndNoSources() {
        // when
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // then
        assertThat(underTest.providerParams()).isEmpty();
        assertThat(underTest.sources()).isEmpty();
    }

    @Test
    void constructor_copiesTheProviderParams() {
        // given
        var providerParams = new HashMap<String, Map<String, String>>();
        providerParams.put("vault", Map.of("address", "one"));

        // when
        var underTest = new SecretsProviderConfig(providerParams, List.of());
        providerParams.put("sops", Map.of("key", "two"));

        // then
        assertThat(underTest.providerParams()).containsOnlyKeys("vault");
    }

    @Test
    void constructor_copiesEachTypesParams() {
        // given
        var params = new HashMap<String, String>();
        params.put("address", "one");

        // when
        var underTest = new SecretsProviderConfig(Map.of("vault", params), List.of());
        params.put("address", "two");

        // then
        assertThat(underTest.paramsFor("vault")).containsOnly(entry("address", "one"));
    }

    @Test
    void constructor_copiesTheSources() {
        // given
        var sources = new ArrayList<SecretsSource>();
        sources.add(FILE_SOURCE);

        // when
        var underTest = new SecretsProviderConfig(Map.of(), sources);
        sources.clear();

        // then
        assertThat(underTest.sources()).containsExactly(FILE_SOURCE);
    }

    @Test
    void constructor_throwsIfProviderParamsIsNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(null, List.of())).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfSourcesIsNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(Map.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAProviderTypeIsBlank() {
        // given
        var providerParams = new HashMap<String, Map<String, String>>();
        providerParams.put("  ", Map.of("address", "one"));

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(providerParams, List.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfATypesParamsAreNull() {
        // given
        var providerParams = new HashMap<String, Map<String, String>>();
        providerParams.put("vault", null);

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(providerParams, List.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAProviderParamValueIsNull() {
        // given
        var params = new HashMap<String, String>();
        params.put("address", null);

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(Map.of("vault", params), List.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfASourceIsNull() {
        // given
        var sources = new ArrayList<SecretsSource>();
        sources.add(null);

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(Map.of(), sources)).isInstanceOf(BratException.class);
    }

    @Test
    void paramsFor_returnsTheParamsConfiguredForTheType() {
        // given
        var underTest =
                new SecretsProviderConfig(Map.of("vault", Map.of("address", "https://vault.example.com")), List.of());

        // when
        var result = underTest.paramsFor("vault");

        // then
        assertThat(result).containsOnly(entry("address", "https://vault.example.com"));
    }

    @Test
    void paramsFor_returnsEmptyForAnUnconfiguredType() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when
        var result = underTest.paramsFor("file");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void paramsFor_throwsIfTypeIsBlank() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when / then
        assertThatThrownBy(() -> underTest.paramsFor(" ")).isInstanceOf(BratException.class);
    }

    @Test
    void withSources_returnsACopyCarryingTheNewSources() {
        // given
        var underTest = new SecretsProviderConfig(Map.of("vault", Map.of("address", "one")), List.of());

        // when
        var result = underTest.withSources(List.of(FILE_SOURCE));

        // then
        assertThat(result.sources()).containsExactly(FILE_SOURCE);
        assertThat(result.providerParams()).containsOnlyKeys("vault");
    }

    @Test
    void withSources_leavesTheOriginalUnchanged() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when
        underTest.withSources(List.of(FILE_SOURCE));

        // then
        assertThat(underTest.sources()).isEmpty();
    }

    @Test
    void withSources_throwsIfTheNewSourcesAreNull() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when / then
        assertThatThrownBy(() -> underTest.withSources(null)).isInstanceOf(BratException.class);
    }
}

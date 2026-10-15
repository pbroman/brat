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
    void constructor_keepsProviderArgsAndSources() {
        // when
        var underTest = new SecretsProviderConfig(
                Map.of("vault", Map.of("address", "https://vault.example.com")), List.of(FILE_SOURCE));

        // then
        assertThat(underTest.providerArgs()).containsOnlyKeys("vault");
        assertThat(underTest.sources()).containsExactly(FILE_SOURCE);
    }

    @Test
    void constructor_acceptsNoArgsAndNoSources() {
        // when
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // then
        assertThat(underTest.providerArgs()).isEmpty();
        assertThat(underTest.sources()).isEmpty();
    }

    @Test
    void constructor_copiesTheProviderArgs() {
        // given
        var providerArgs = new HashMap<String, Map<String, String>>();
        providerArgs.put("vault", Map.of("address", "one"));

        // when
        var underTest = new SecretsProviderConfig(providerArgs, List.of());
        providerArgs.put("sops", Map.of("key", "two"));

        // then
        assertThat(underTest.providerArgs()).containsOnlyKeys("vault");
    }

    @Test
    void constructor_copiesEachTypesArgs() {
        // given
        var args = new HashMap<String, String>();
        args.put("address", "one");

        // when
        var underTest = new SecretsProviderConfig(Map.of("vault", args), List.of());
        args.put("address", "two");

        // then
        assertThat(underTest.argsFor("vault")).containsOnly(entry("address", "one"));
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
    void constructor_throwsIfProviderArgsIsNull() {
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
        var providerArgs = new HashMap<String, Map<String, String>>();
        providerArgs.put("  ", Map.of("address", "one"));

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(providerArgs, List.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfATypesArgsAreNull() {
        // given
        var providerArgs = new HashMap<String, Map<String, String>>();
        providerArgs.put("vault", null);

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(providerArgs, List.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAProviderParamValueIsNull() {
        // given
        var args = new HashMap<String, String>();
        args.put("address", null);

        // when / then
        assertThatThrownBy(() -> new SecretsProviderConfig(Map.of("vault", args), List.of()))
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
    void argsFor_returnsTheArgsConfiguredForTheType() {
        // given
        var underTest =
                new SecretsProviderConfig(Map.of("vault", Map.of("address", "https://vault.example.com")), List.of());

        // when
        var result = underTest.argsFor("vault");

        // then
        assertThat(result).containsOnly(entry("address", "https://vault.example.com"));
    }

    @Test
    void argsFor_returnsEmptyForAnUnconfiguredType() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when
        var result = underTest.argsFor("file");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void argsFor_throwsIfTypeIsBlank() {
        // given
        var underTest = new SecretsProviderConfig(Map.of(), List.of());

        // when / then
        assertThatThrownBy(() -> underTest.argsFor(" ")).isInstanceOf(BratException.class);
    }

    @Test
    void withSources_returnsACopyCarryingTheNewSources() {
        // given
        var underTest = new SecretsProviderConfig(Map.of("vault", Map.of("address", "one")), List.of());

        // when
        var result = underTest.withSources(List.of(FILE_SOURCE));

        // then
        assertThat(result.sources()).containsExactly(FILE_SOURCE);
        assertThat(result.providerArgs()).containsOnlyKeys("vault");
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

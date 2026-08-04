package dev.pbroman.brat.core.interpolation.rules;

import java.util.Optional;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecretsInterpolationRuleTest extends AbstractInterpolationTest {

    private final SecretsProvider provider = mock(SecretsProvider.class);

    @BeforeEach
    void setUp() {
        underTest = new SecretsInterpolationRule(provider, patterns);
    }

    @Test
    void outcome_resolvesSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = underTest.outcome("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("s3cr3t");
    }

    @Test
    void outcome_marksOutcomeAsContainingSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = underTest.outcome("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.containsSecret()).isTrue();
    }

    @Test
    void outcome_reportingStringDoesNotContainTheSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = underTest.outcome("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.reportingString()).doesNotContain("s3cr3t").isEqualTo("${secrets.apiKey} → ***");
    }

    @Test
    void outcome_passesThroughATokenOfAnotherNamespace() {
        // given
        var input = "${constants.moo}";

        // when
        var result = underTest.outcome(input, runtimeData);

        // then
        assertThat(result.value()).isEqualTo(input);
        assertThat(result.containsSecret()).isFalse();
    }

    @Test
    void outcome_throwsIfNoProviderHasTheSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> underTest.outcome("${secrets.apiKey}", runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void outcome_propagatesProviderFailure() {
        // given
        when(provider.getSecret("apiKey")).thenThrow(new BratException("vault unreachable"));

        // then
        assertThatThrownBy(() -> underTest.outcome("${secrets.apiKey}", runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void outcome_throwsIfTheTokenHasNoKey() {
        // then
        assertThatThrownBy(() -> underTest.outcome("${secrets.}", runtimeData)).isInstanceOf(BratException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void constructor_throwsOnNullProvider() {
        // then
        assertThatThrownBy(() -> new SecretsInterpolationRule(null, patterns)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullTools() {
        // then
        assertThatThrownBy(() -> new SecretsInterpolationRule(provider, null)).isInstanceOf(BratException.class);
    }

    @Test
    void outcome_treatsFallbackDelimiterAsPartOfTheKey() {
        // given
        when(provider.getSecret("apiKey:-env.fallback")).thenReturn(Optional.of("literalKey"));

        // when
        var result = underTest.outcome("${secrets.apiKey:-env.fallback}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("literalKey");
    }
}

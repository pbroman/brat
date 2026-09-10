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

    @Override
    protected String ownToken() {
        return "${secrets.moo}";
    }

    private final SecretsProvider provider = mock(SecretsProvider.class);

    @BeforeEach
    void setUp() {
        underTest = new SecretsInterpolationRule(provider);
    }

    @Test
    void outcome_resolvesSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = claimed("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("s3cr3t");
    }

    @Test
    void outcome_marksOutcomeAsContainingSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = claimed("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.containsSecret()).isTrue();
    }

    @Test
    void outcome_reportingStringDoesNotContainTheSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.of("s3cr3t"));

        // when
        var result = claimed("${secrets.apiKey}", runtimeData);

        // then
        assertThat(result.reportingString()).doesNotContain("s3cr3t").isEqualTo("${secrets.apiKey} → ***");
    }

    @Test
    void outcome_passesThroughATokenOfAnotherNamespace() {
        // given
        var input = "${constants.moo}";

        // when
        var result = claimed(input, runtimeData);

        // then
        assertThat(result.value()).isEqualTo(input);
        assertThat(result.containsSecret()).isFalse();
    }

    @Test
    void outcome_throwsIfNoProviderHasTheSecret() {
        // given
        when(provider.getSecret("apiKey")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> claimed("${secrets.apiKey}", runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void outcome_propagatesProviderFailure() {
        // given
        when(provider.getSecret("apiKey")).thenThrow(new BratException("vault unreachable"));

        // then
        assertThatThrownBy(() -> claimed("${secrets.apiKey}", runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void outcome_throwsIfTheTokenHasNoKey() {
        // then
        assertThatThrownBy(() -> claimed("${secrets.}", runtimeData)).isInstanceOf(BratException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void constructor_throwsOnNullProvider() {
        // then
        assertThatThrownBy(() -> new SecretsInterpolationRule(null)).isInstanceOf(BratException.class);
    }

    @Test
    void outcome_treatsFallbackDelimiterAsPartOfTheKey() {
        // given
        when(provider.getSecret("apiKey:-env.fallback")).thenReturn(Optional.of("literalKey"));

        // when
        var result = claimed("${secrets.apiKey:-env.fallback}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("literalKey");
    }

    @Test
    void outcome_declinesANestedTokenWithoutAskingTheProvider() {
        // when — a secret named by another token; nothing in core computes one
        var outcome = underTest.outcome("${secrets.${vars.which}}", runtimeData);

        // then — declined, and no lookup attempted for a key BRAT cannot read as authored
        assertThat(outcome).isEmpty();
        verifyNoInteractions(provider);
    }

    @Test
    void outcome_declinesTextThatMerelyContainsASecretsToken() {
        // when — a rule is handed one token at a time; surrounding text is the scanner's business
        var outcome = underTest.outcome("Bearer ${secrets.apiKey}", runtimeData);

        // then
        assertThat(outcome).isEmpty();
        verifyNoInteractions(provider);
    }
}

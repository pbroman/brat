package dev.pbroman.brat.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class CompositeSecretsProviderTest {

    private final SecretsProvider first = mock(SecretsProvider.class);

    private final SecretsProvider second = mock(SecretsProvider.class);

    @Test
    void getSecret_returnsFirstHit() {
        // given
        when(first.getSecret("apiKey")).thenReturn(Optional.of("fromFirst"));
        when(second.getSecret("apiKey")).thenReturn(Optional.of("fromSecond"));
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).contains("fromFirst");
    }

    @Test
    void getSecret_skipsProvidersWithoutTheKey() {
        // given
        when(first.getSecret("apiKey")).thenReturn(Optional.empty());
        when(second.getSecret("apiKey")).thenReturn(Optional.of("fromSecond"));
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).contains("fromSecond");
    }

    @Test
    void getSecret_doesNotConsultProvidersAfterTheHit() {
        // given
        when(first.getSecret("apiKey")).thenReturn(Optional.of("fromFirst"));
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // when
        underTest.getSecret("apiKey");

        // then
        verify(second, never()).getSecret("apiKey");
    }

    @Test
    void getSecret_returnsEmptyIfNoProviderHasTheKey() {
        // given
        when(first.getSecret("apiKey")).thenReturn(Optional.empty());
        when(second.getSecret("apiKey")).thenReturn(Optional.empty());
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_returnsEmptyForAnEmptyChain() {
        // given
        var underTest = new CompositeSecretsProvider(List.of());

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_abortsOnProviderFailure() {
        // given
        when(first.getSecret("apiKey")).thenThrow(new BratException("vault unreachable"));
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // then
        assertThatThrownBy(() -> underTest.getSecret("apiKey"))
                .isInstanceOf(BratException.class);
        verify(second, never()).getSecret("apiKey");
    }

    @Test
    void getSecret_throwsOnNullKey() {
        // given
        var underTest = new CompositeSecretsProvider(List.of(first));

        // then
        assertThatThrownBy(() -> underTest.getSecret(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void getSecret_throwsOnBlankKey() {
        // given
        var underTest = new CompositeSecretsProvider(List.of(first));

        // then
        assertThatThrownBy(() -> underTest.getSecret("  "))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullProviders() {
        // then
        assertThatThrownBy(() -> new CompositeSecretsProvider(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullElement() {
        // given
        var providers = Arrays.asList(first, null);

        // then
        assertThatThrownBy(() -> new CompositeSecretsProvider(providers))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_copiesTheProviderList() {
        // given
        when(first.getSecret("apiKey")).thenReturn(Optional.of("fromFirst"));
        var providers = new ArrayList<SecretsProvider>();
        providers.add(first);
        var underTest = new CompositeSecretsProvider(providers);

        // when
        providers.clear();

        // then
        assertThat(underTest.getSecret("apiKey")).contains("fromFirst");
    }

    @Test
    void close_closesEveryProviderInOrder() {
        // given
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // when
        underTest.close();

        // then
        var inOrder = inOrder(first, second);
        inOrder.verify(first).close();
        inOrder.verify(second).close();
    }

    @Test
    void close_closesEveryProviderEvenIfOneFails() {
        // given
        doThrow(new BratException("cannot close")).when(first).close();
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // then
        assertThatThrownBy(underTest::close)
                .isInstanceOf(BratException.class);
        verify(second).close();
    }

    @Test
    void close_reportsTheFirstFailureAsTheCause() {
        // given
        var firstFailure = new BratException("first cannot close");
        doThrow(firstFailure).when(first).close();
        doThrow(new BratException("second cannot close")).when(second).close();
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // then
        assertThatThrownBy(underTest::close)
                .isInstanceOf(BratException.class)
                .cause().isSameAs(firstFailure);
    }

    @Test
    void close_attachesLaterFailuresAsSuppressed() {
        // given
        var secondFailure = new BratException("second cannot close");
        doThrow(new BratException("first cannot close")).when(first).close();
        doThrow(secondFailure).when(second).close();
        var underTest = new CompositeSecretsProvider(List.of(first, second));

        // then
        assertThatThrownBy(underTest::close)
                .isInstanceOf(BratException.class)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(secondFailure));
    }
}

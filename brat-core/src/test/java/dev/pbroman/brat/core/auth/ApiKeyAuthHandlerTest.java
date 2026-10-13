package dev.pbroman.brat.core.auth;

import java.util.Map;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyAuthHandlerTest {

    private static final HttpRequestDefinition REQUEST =
            new HttpRequestDefinition("http://localhost/x", "GET", null, null);

    private final ApiKeyAuthHandler underTest = new ApiKeyAuthHandler();

    @Test
    void type_isApikey() {
        // then
        assertThat(underTest.type()).isEqualTo("apikey");
    }

    @Test
    void apply_sendsTheKeyWithTheApiKeySchemeWhenNoHeaderIsGiven() {
        // given
        var auth = new Auth("apikey", Map.of("key", "k-123"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then
        assertThat(headers).containsExactlyEntriesOf(Map.of("Authorization", "ApiKey k-123"));
    }

    @Test
    void apply_sendsTheKeyRawInTheNamedHeader() {
        // given
        var auth = new Auth("apikey", Map.of("key", "k-123", "header", "x-api-key"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then - no scheme, and no Authorization header beside it
        assertThat(headers).containsExactlyEntriesOf(Map.of("x-api-key", "k-123"));
    }

    @Test
    void apply_rejectsAnUnknownArgNamingItWithoutQuotingTheKey() {
        // given
        var auth = new Auth("apikey", Map.of("key", "k-123", "scheme", "Token"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("scheme")
                .hasMessageNotContaining("k-123");
    }

    @Test
    void apply_rejectsAMissingKey() {
        // given
        var auth = new Auth("apikey", Map.of("header", "x-api-key"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'key'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void apply_rejectsABlankKey(String blank) {
        // given
        var auth = new Auth("apikey", Map.of("key", blank));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'key'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void apply_rejectsABlankHeaderWithoutQuotingTheKey(String blank) {
        // given
        var auth = new Auth("apikey", Map.of("key", "k-123", "header", blank));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("header")
                .hasMessageNotContaining("k-123");
    }
}

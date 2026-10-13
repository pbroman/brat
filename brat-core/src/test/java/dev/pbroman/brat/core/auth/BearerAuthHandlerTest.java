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

class BearerAuthHandlerTest {

    private static final HttpRequestDefinition REQUEST =
            new HttpRequestDefinition("http://localhost/x", "GET", null, null);

    private final BearerAuthHandler underTest = new BearerAuthHandler();

    @Test
    void type_isBearer() {
        // then
        assertThat(underTest.type()).isEqualTo("bearer");
    }

    @Test
    void apply_returnsExactlyTheAuthorizationHeader() {
        // given
        var auth = new Auth("bearer", Map.of("token", "abc.def.ghi"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then
        assertThat(headers).containsExactlyEntriesOf(Map.of("Authorization", "Bearer abc.def.ghi"));
    }

    @Test
    void apply_rejectsAnUnknownArgNamingItWithoutQuotingTheToken() {
        // given
        var auth = new Auth("bearer", Map.of("token", "abc.def.ghi", "username", "admin"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("username")
                .hasMessageNotContaining("abc.def.ghi");
    }

    @Test
    void apply_rejectsAMissingToken() {
        // given
        var auth = new Auth("bearer", null);

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void apply_rejectsABlankToken(String blank) {
        // given - the shape of a login whose capture has not run yet
        var auth = new Auth("bearer", Map.of("token", blank));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("token");
    }
}

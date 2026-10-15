package dev.pbroman.brat.core.auth;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasicAuthHandlerTest {

    private static final HttpRequestDefinition REQUEST =
            new HttpRequestDefinition("http://localhost/x", "GET", null, null);

    private final BasicAuthHandler underTest = new BasicAuthHandler();

    @Test
    void type_isBasic() {
        // then
        assertThat(underTest.type()).isEqualTo("basic");
    }

    @Test
    void apply_returnsExactlyTheAuthorizationHeader() {
        // given
        var auth = new Auth("basic", Map.of("username", "admin", "password", "secret"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then - base64 of "admin:secret"
        assertThat(headers).containsExactlyEntriesOf(Map.of("Authorization", "Basic YWRtaW46c2VjcmV0"));
    }

    @Test
    void apply_encodesTheCredentialsAsUtf8() {
        // given
        var auth = new Auth("basic", Map.of("username", "jörg", "password", "pässwörd"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then - base64 of the UTF-8 bytes of "jörg:pässwörd"
        assertThat(headers).containsEntry("Authorization", "Basic asO2cmc6cMOkc3N3w7ZyZA==");
    }

    @Test
    void apply_allowsAColonInThePassword() {
        // given
        var auth = new Auth("basic", Map.of("username", "admin", "password", "se:cret"));

        // when
        var headers = underTest.apply(auth, REQUEST);

        // then - only the first colon separates, so a password may hold one
        assertThat(headers).containsEntry("Authorization", "Basic YWRtaW46c2U6Y3JldA==");
    }

    @Test
    void apply_rejectsAColonInTheUsernameWithoutQuotingIt() {
        // given
        var auth = new Auth("basic", Map.of("username", "ad:min", "password", "secret"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("username")
                .hasMessageNotContaining("ad:min");
    }

    @Test
    void apply_rejectsAnUnknownArgNamingIt() {
        // given
        var auth = new Auth("basic", Map.of("username", "admin", "password", "secret", "token", "t0k3n"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("token")
                .hasMessageNotContaining("t0k3n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password"})
    void apply_rejectsAMissingArgNamingIt(String missing) {
        // given
        var args = new HashMap<>(Map.of("username", "admin", "password", "secret"));
        args.remove(missing);

        // when / then
        assertThatThrownBy(() -> underTest.apply(new Auth("basic", args), REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(missing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password"})
    void apply_rejectsABlankArgNamingIt(String blank) {
        // given - an unset variable or a capture that never ran resolves to empty text
        var args = new HashMap<>(Map.of("username", "admin", "password", "secret"));
        args.put(blank, "  ");

        // when / then
        assertThatThrownBy(() -> underTest.apply(new Auth("basic", args), REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(blank);
    }

    @Test
    void apply_neverQuotesThePasswordInAnError() {
        // given - fails on the unknown arg, while holding a real password
        var auth = new Auth("basic", Map.of("username", "admin", "password", "hunter2", "extra", "x"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST)).hasMessageNotContaining("hunter2");
    }
}

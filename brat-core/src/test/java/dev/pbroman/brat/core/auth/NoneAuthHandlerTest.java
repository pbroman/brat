package dev.pbroman.brat.core.auth;

import java.util.Map;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoneAuthHandlerTest {

    private static final HttpRequestDefinition REQUEST =
            new HttpRequestDefinition("http://localhost/x", "GET", null, null);

    private final NoneAuthHandler underTest = new NoneAuthHandler();

    @Test
    void type_isNone() {
        // then
        assertThat(underTest.type()).isEqualTo("none");
    }

    @Test
    void apply_returnsNoHeaders() {
        // when
        var headers = underTest.apply(new Auth("none", null), REQUEST);

        // then
        assertThat(headers).isNotNull().isEmpty();
    }

    @Test
    void apply_rejectsAnyArgNamingIt() {
        // given
        var auth = new Auth("none", Map.of("token", "s3cr3t"));

        // when / then
        assertThatThrownBy(() -> underTest.apply(auth, REQUEST))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("token")
                .hasMessageNotContaining("s3cr3t");
    }
}

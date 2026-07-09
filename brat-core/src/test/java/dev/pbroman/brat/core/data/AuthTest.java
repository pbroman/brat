package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_APIKEY;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BASIC;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BEARER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


class AuthTest extends AbstractConfigDataTest {

    static Auth authNone = new Auth();
    static Auth authBasic = new Auth(AUTH_TYPE_BASIC, "username", "password");
    static Auth authBearer = new Auth(AUTH_TYPE_BEARER, "token");
    static Auth authApiKey = new Auth(AUTH_TYPE_APIKEY, "apiKey");

    @Test
    void interpolatedBasic_isCorrect() {
        // when
        var interpolated = authBasic.interpolated(interpolation, runtimeData);

        // then
        assertThat(interpolated.getType()).isEqualTo("interpolated");
        assertThat(interpolated.getUsername()).isEqualTo("interpolated");
        assertThat(interpolated.getPassword()).isEqualTo("interpolated");
        assertThat(interpolated.getReportingString()).isNotNull();
    }

    @Test
    void interpolatedBearer_isCorrect() {
        // when
        var interpolated = authBearer.interpolated(interpolation, runtimeData);

        // then
        assertThat(interpolated.getType()).isEqualTo("interpolated");
        assertThat(interpolated.getToken()).isEqualTo("interpolated");
        assertThat(interpolated.getReportingString()).isNotNull();
    }

    @Test
    void interpolatedApiKey_isCorrect() {
        // when
        var interpolated = authApiKey.interpolated(interpolation, runtimeData);

        // then
        assertThat(interpolated.getType()).isEqualTo("interpolated");
        assertThat(interpolated.getToken()).isEqualTo("interpolated");
        assertThat(interpolated.getReportingString()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("provideAllAuthTypes")
    void interpolated_throwsExceptionIfCopy(Auth auth) {
        assertInterpolatedThrowsExceptionIfCopy(auth);
    }

    private static Stream<Arguments> provideAllAuthTypes() {
        return Stream.of(
                Arguments.of(authNone),
                Arguments.of(authBasic),
                Arguments.of(authBearer),
                Arguments.of(authApiKey)
        );
    }

}
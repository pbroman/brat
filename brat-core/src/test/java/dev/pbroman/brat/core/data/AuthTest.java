package dev.pbroman.brat.core.data;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTest {

    @Test
    void constructor_defaultsNullArgsToEmpty() {
        // when
        var auth = new Auth("none", null);

        // then
        assertThat(auth.getArgs()).isNotNull().isEmpty();
    }

    @Test
    void constructor_copiesTheArgsAndHandsOutAnUnmodifiableMap() {
        // given
        var args = new HashMap<String, String>();
        args.put("token", "t");

        // when
        var auth = new Auth("bearer", args);
        args.put("other", "x");

        // then
        assertThat(auth.getArgs()).containsOnlyKeys("token");
        assertThatThrownBy(() -> auth.getArgs().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_keepsTheArgsInDeclarationOrder() {
        // given
        var args = new java.util.LinkedHashMap<String, String>();
        args.put("username", "u");
        args.put("password", "p");

        // when
        var auth = new Auth("basic", args);

        // then
        assertThat(auth.getArgs().keySet()).containsExactly("username", "password");
    }

    @Test
    void constructor_keepsANullArgValueForInterpolationToReject() {
        // given - authored YAML 'token:' with nothing after it
        var args = new HashMap<String, String>();
        args.put("token", null);

        // when
        var auth = new Auth("bearer", args);

        // then
        assertThat(auth.getArgs()).containsEntry("token", null);
    }

    @Test
    void constructor_isNotAnInterpolatedCopyWithoutOutcomes() {
        // when
        var auth = new Auth("bearer", Map.of("token", "t"));

        // then
        assertThat(auth.isInterpolated()).isFalse();
    }
}

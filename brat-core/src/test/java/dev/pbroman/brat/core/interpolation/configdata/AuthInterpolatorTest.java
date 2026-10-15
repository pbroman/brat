package dev.pbroman.brat.core.interpolation.configdata;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterpolatorTest {

    private final AuthInterpolator interpolator = new AuthInterpolator();

    private Interpolation interpolation;
    private RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        runtimeData = mock(RuntimeData.class);
        // resolves every input to "r:<input>", reported as "<input> → r:<input>", never secret
        when(interpolation.outcome(anyString(), any())).thenAnswer(i -> {
            String input = i.getArgument(0);
            return new InterpolationOutcome("r:" + input, input + " → r:" + input);
        });
    }

    @Test
    void interpolated_interpolatesTheTypeAndEveryArg() {
        // given
        var auth = new Auth("${vars.type}", ordered("username", "${env.user}", "password", "hunter2"));

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        assertThat(interpolated.getType()).isEqualTo("r:${vars.type}");
        assertThat(interpolated.getArgs())
                .containsExactlyEntriesOf(ordered("username", "r:${env.user}", "password", "r:hunter2"));
        assertThat(interpolated.isInterpolated()).isTrue();
    }

    @Test
    void interpolated_recordsTypeThenEachArgInDeclarationOrder() {
        // given
        var auth = new Auth("basic", ordered("username", "u", "password", "p"));

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        assertThat(interpolated.getOutcomes().keySet()).containsExactly("type", "args.username", "args.password");
    }

    @Test
    void interpolated_leavesTheTypeOutcomeReadable() {
        // given
        var auth = new Auth("bearer", Map.of("token", "t"));

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        var type = interpolated.getOutcomes().get("type");
        assertThat(type.containsSecret()).isFalse();
        assertThat(type.reportingString()).isEqualTo("bearer → r:bearer");
    }

    @Test
    void interpolated_masksEveryArgWholeEvenALiteral() {
        // given - a literal written straight into the suite is masked as surely as a secret
        var auth = new Auth("basic", ordered("username", "admin", "password", "hunter2"));

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        for (var key : new String[] {"args.username", "args.password"}) {
            var outcome = interpolated.getOutcomes().get(key);
            assertThat(outcome.containsSecret()).isTrue();
            assertThat(outcome.reportingString()).isEqualTo("***");
        }
    }

    @Test
    void interpolated_keepsTheResolvedValueInAMaskedOutcome() {
        // given
        var auth = new Auth("bearer", Map.of("token", "${vars.t}"));

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        assertThat(interpolated.getOutcomes().get("args.token").value()).isEqualTo("r:${vars.t}");
    }

    @Test
    void interpolated_leavesEmptyArgsEmptyAndRecordsOnlyTheType() {
        // given
        var auth = new Auth("none", null);

        // when
        var interpolated = interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        assertThat(interpolated.getArgs()).isEmpty();
        assertThat(interpolated.getOutcomes()).containsOnlyKeys("type");
    }

    @Test
    void interpolated_leavesTheAuthoredBlockUnchanged() {
        // given
        var auth = new Auth("bearer", Map.of("token", "${vars.t}"));

        // when
        interpolator.interpolated(auth, interpolation, runtimeData);

        // then
        assertThat(auth.isInterpolated()).isFalse();
        assertThat(auth.getArgs()).containsEntry("token", "${vars.t}");
    }

    @Test
    void interpolated_throwsOnANullArgValueNamingTheArg() {
        // given
        var args = new HashMap<String, String>();
        args.put("token", null);
        var auth = new Auth("bearer", args);

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(auth, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("token");
    }

    @Test
    void interpolated_throwsOnANullType() {
        // given
        var auth = new Auth(null, Map.of("token", "t"));

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(auth, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("type");
    }

    @Test
    void interpolated_throwsOnNull() {
        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(null, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsIfCopy() {
        // given
        var copy = interpolator.interpolated(new Auth("bearer", Map.of("token", "t")), interpolation, runtimeData);

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(copy, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    private static Map<String, String> ordered(String k1, String v1, String k2, String v2) {
        var map = new LinkedHashMap<String, String>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}

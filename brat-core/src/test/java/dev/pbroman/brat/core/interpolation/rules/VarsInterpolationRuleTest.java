package dev.pbroman.brat.core.interpolation.rules;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VarsInterpolationRuleTest extends AbstractInterpolationTest {

    @Override
    protected String ownToken() {
        return "${vars.moo}";
    }

    @BeforeEach
    void setUp() {
        underTest = new VarsInterpolationRule();
    }

    protected RuntimeData setUpRuntimeData() {
        var vars = new HashMap<String, Object>();
        vars.put("moo", "baa");
        return new RuntimeData(Map.of(), Map.of(), vars);
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${vars.moo}";
        var expected = "baa";

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_returnsEmptyStringForAVariableNeverSet() throws Exception {
        // given
        var input = "${vars.missing}";
        var expected = "";

        // when
        var result = interpolate(input, runtimeData);

        // then - vars is deliberately the one soft-failing namespace
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_throwsForAVariableWhoseCaptureFailed() {
        // given
        runtimeData.setCurrentPath("happy path/create an order");
        runtimeData.captureFailed("orderId", "no such path");

        // when / then - the message must carry both halves, since the reader is standing elsewhere
        assertThatThrownBy(() -> interpolate("${vars.orderId}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("orderId")
                .hasMessageContaining("no such path")
                .hasMessageContaining("happy path/create an order");
    }

    @Test
    void onMissingReplacement_readsUnknownForATombstoneWithNoPath() {
        // given - nothing set a current path, so the tombstone recorded none
        runtimeData.captureFailed("orderId", "no such path");

        // when / then - a missing location must not print as "null"
        assertThatThrownBy(() -> interpolate("${vars.orderId}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("unknown")
                .hasMessageNotContaining("null");
    }

    @Test
    void onMissingReplacement_letsAnAuthoredFallbackWinOverATombstone() {
        // given
        runtimeData.captureFailed("orderId", "no such path");

        // when - the author has said what to do when the value is unavailable
        var result = interpolate("${vars.orderId:-none}", runtimeData);

        // then
        assertThat(result).isEqualTo("none");
    }

    @Test
    void onMissingReplacement_returnsEmptyStringAgainOnceACaptureSucceeds() {
        // given - a failed capture, then a later one that worked
        runtimeData.captureFailed("orderId", "no such path");
        runtimeData.captureVar("orderId", "42");

        // when
        var result = interpolate("${vars.orderId}", runtimeData);

        // then
        assertThat(result).isEqualTo("42");
    }
}

package dev.pbroman.brat.core.data.result;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionResultTest {

    @Test
    void toString_masksASecretInTheCondition() {
        // given
        var interpolated = new Condition(
                "isEqualTo",
                "s3cr3t",
                "abc",
                null,
                Map.of(
                        "a", new InterpolationOutcome("s3cr3t", "${secrets.token} → ***", true),
                        "b", new InterpolationOutcome("abc", "abc")));
        var result = new AssertionResult(interpolated, "token must match", false);

        // when
        var rendered = result.toString();

        // then
        assertThat(rendered).contains("***").doesNotContain("s3cr3t");
    }

    @Test
    void toString_doesNotCallAPassingResultFailed() {
        // given
        var result = new AssertionResult(new Condition("isEqualTo", "a", "a"), null, true);

        // when
        var rendered = result.toString();

        // then
        assertThat(rendered).doesNotContain("failed").contains("passed=true");
    }

    @Test
    void constructor_defaultsANullSeverityToFail() {
        // when
        var result = new AssertionResult(new Condition("isEqualTo", "a", "b"), null, false, null);

        // then - so a failed result with no declared severity still fails its request
        assertThat(result.severity()).isEqualTo(AssertionSeverity.FAIL);
    }
}

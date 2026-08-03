package dev.pbroman.brat.core.interpolation;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import org.junit.jupiter.api.Test;

class InterpolationChecksTest {

    private final RuntimeData runtimeData = new RuntimeData(Map.of("stage", "dev"), Map.of());

    @Test
    void requireNamespaces_doesNothingWhenEveryNamespaceIsPresent() {
        // when / then
        assertThatCode(() -> InterpolationChecks.requireNamespaces(runtimeData, CONSTANTS, ENV))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNamespaces_doesNothingWhenNoNamespaceIsNamed() {
        // when / then
        assertThatCode(() -> InterpolationChecks.requireNamespaces(runtimeData)).doesNotThrowAnyException();
    }

    @Test
    void requireNamespaces_throwsWhenTheRuntimeDataIsNull() {
        // when / then
        assertThatThrownBy(() -> InterpolationChecks.requireNamespaces(null, CONSTANTS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNamespaces_throwsWhenTheRuntimeDataIsNullAndNoNamespaceIsNamed() {
        // when / then
        assertThatThrownBy(() -> InterpolationChecks.requireNamespaces(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNamespaces_throwsWhenANamedNamespaceIsAbsent() {
        // given
        var withoutConstants = new RuntimeData(null, Map.of());

        // when / then
        assertThatThrownBy(() -> InterpolationChecks.requireNamespaces(withoutConstants, CONSTANTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CONSTANTS);
    }

    @Test
    void requireNamespaces_namesEveryMissingNamespaceNotOnlyTheFirst() {
        // given
        var withoutConstantsOrEnv = new RuntimeData(null, null);

        // when / then
        assertThatThrownBy(() -> InterpolationChecks.requireNamespaces(withoutConstantsOrEnv, CONSTANTS, ENV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CONSTANTS)
                .hasMessageContaining(ENV);
    }

    @Test
    void requireNamespaces_ignoresPresentNamespacesWhenReportingMissingOnes() {
        // given
        var withoutEnv = new RuntimeData(Map.of("stage", "dev"), null);

        // when / then
        assertThatThrownBy(() -> InterpolationChecks.requireNamespaces(withoutEnv, CONSTANTS, ENV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ENV)
                .hasMessageNotContaining("'" + CONSTANTS + "'");
    }
}

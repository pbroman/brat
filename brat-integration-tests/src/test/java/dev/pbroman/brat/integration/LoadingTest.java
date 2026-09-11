package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loading a suite from a real file, and what an author is told when the file is wrong.
 *
 * <p>The loader's rules are pinned by unit tests and are not repeated here. What only this module
 * reaches is the file itself: a resource read off the classpath, its name carried into the error as
 * the origin, and an anchored document surviving composition all the way into a run.
 */
class LoadingTest extends EndToEndTestBase {

    /** The credential-shaped value in the broken suite. Nothing about an error may contain it. */
    private static final String MUST_NOT_LEAK = "do-not-print-me-anywhere";

    @Test
    void load_namesTheResourceAndThePositionAndNeverTheValue() {
        // when
        var thrown = assertThatThrownBy(() -> suite("suites/broken/unknown-key.yaml"));

        // then — the resource, the key and where it sat
        thrown.isInstanceOf(BratException.class)
                .hasMessageContaining("suites/broken/unknown-key.yaml")
                .hasMessageContaining("responsActions")
                .hasMessageMatching("(?s).*line \\d+, column \\d+.*");

        // and — a suite file may hold a credential, and an error travels into CI logs
        thrown.hasMessageNotContaining(MUST_NOT_LEAK).hasMessageNotContaining("Bearer");
    }

    @Test
    void run_resolvesAnchorsAndMergeKeysInAFileThatThenRuns() {
        // when — the second request inherits the first's definition and overrides its url
        var suite = suite("suites/anchored-requests.yaml");
        var result = run(suite, Map.of());

        // then — every assertion of both requests ran, which is what says the merge key resolved
        assertPassed(result, suite);
        assertThat(result.requestResults()).hasSize(2);
    }
}

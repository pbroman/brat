package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code form:} body, against a server that decodes it.
 *
 * <p>Only a real server shows that the encoding is the one a server reads: a value holding the
 * characters that delimit a form arrives as written, and the {@code Content-Type} BRAT set is the one
 * that made the server parse it as a form at all.
 */
class FormBodyTest extends EndToEndTestBase {

    @Test
    void run_sendsAFormTheServerReadsAsWritten() {
        // when
        var suite = suite("suites/form-body.brat.yaml");
        var result = run(suite, Map.of("tricky", "a&b=c d+e 100% grün"));

        // then
        assertPassed(result, suite);
    }

    @Test
    void run_reportsTheContentTypeItAdded() {
        // when
        var result = run("suites/form-body.brat.yaml", Map.of("tricky", "x"));

        // then - the request as sent shows the header no one wrote, and says where it came from
        var sent = (HttpRequestDefinition) result.requestResults().getFirst().requestDefinition();
        assertThat(sent.getHeaders()).containsEntry("Content-Type", "application/x-www-form-urlencoded");
        assertThat(sent.getOutcomes().get("header.Content-Type").reportingString())
                .isEqualTo("body form → application/x-www-form-urlencoded");
    }
}

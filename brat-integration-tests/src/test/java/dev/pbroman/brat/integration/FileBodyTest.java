package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request body read from a file.
 *
 * <p>The file is resolved by the interpolator, not by a handler — so the path may hold tokens, the
 * file's own content may hold them too, and only a real request shows that what reached the server
 * was the resolved text rather than the template.
 */
class FileBodyTest extends EndToEndTestBase {

    @Test
    void run_sendsABodyReadFromAFileNamedByAToken() {
        // when — the path is built from ${env.bodyFile}, so the read must happen after interpolation
        var suite = suite("suites/file-body.yaml");
        var result = run(suite, Map.of("bodyFile", "create-user.json", "street", "Sesame Street"));

        // then
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);

        // and — the server stored what the file said, nested object included
        var stored = crud.get("/search/from-a-file");
        assertThat(stored.size()).isEqualTo(1);
        assertThat(stored.get(0).get("address").get("city").asString()).isEqualTo("Testville");

        // and — the token *inside* the file was resolved before it was sent
        assertThat(stored.get(0).get("address").get("street").asString()).isEqualTo("Sesame Street");
    }

    @Test
    void run_errorsWhenTheBodyFileIsNotThere() {
        // when
        var result = run("suites/file-body.yaml", Map.of("bodyFile", "no-such-file.json", "street", "unused"));

        // then — a missing file fails at request time, naming the path
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(((RequestStatus.Errored) request.status()).message()).contains("no-such-file.json");
    }
}

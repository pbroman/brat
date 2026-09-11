package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request body read from a file.
 *
 * <p>The file is read inside the handler, after the definition is interpolated — so the path may hold
 * tokens, and only a real request shows that the file's content reached the server intact. Tokens
 * <em>inside</em> the file are a separate matter and are deliberately not asserted here: nothing
 * interpolates a body file's content today, which is recorded as a finding rather than pinned as
 * behaviour.
 */
class FileBodyTest extends EndToEndTestBase {

    @Test
    void run_sendsABodyReadFromAFileNamedByAToken() {
        // when — the path is built from ${env.bodyFile}, so the read must happen after interpolation
        var suite = suite("suites/file-body.yaml");
        var result = run(suite, Map.of("bodyFile", "create-user.json"));

        // then
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);

        // and — the server stored what the file said, nested object included
        var stored = crud.get("/search/from-a-file");
        assertThat(stored.size()).isEqualTo(1);
        assertThat(stored.get(0).get("address").get("city").asString()).isEqualTo("Testville");
    }

    @Test
    void run_errorsWhenTheBodyFileIsNotThere() {
        // when
        var result = run("suites/file-body.yaml", Map.of("bodyFile", "no-such-file.json"));

        // then — a missing file fails at request time, naming the path
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(((RequestStatus.Errored) request.status()).message()).contains("no-such-file.json");
    }
}

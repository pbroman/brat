package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vertical slice: a suite loaded from a real file, interpolated against a real environment,
 * executed against a real server, asserted over a real response body.
 *
 * <p>What only an assembly can show here is the body itself — a nested object, an indexed array, a
 * JSON number compared numerically and a timestamp a real serializer produced. A unit test asserting
 * over a hand-written body proves the func; it cannot prove the body ever looked like that.
 */
class OneRequestTest extends EndToEndTestBase {

    private static final String SEEDED = """
            {
              "username": "seeded",
              "age": 42,
              "active": true,
              "roles": ["admin", "reader"],
              "address": {"street": "Test Street 1", "city": "Testville", "zip": "00000"}
            }""";

    @Test
    void run_assertsOverAGenuineResponseBody() {
        // given
        var seeded = crud.create(SEEDED);

        // when
        var suite = suite("suites/read-one-user.yaml");
        var result = run(suite, Map.of("userId", seeded.get("id").asString()));

        // then
        assertPassed(result, suite);
        assertThat(result.requestResults()).hasSize(1);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void run_reportsAFailedAssertionWithoutEndingTheRun() {
        // given — the suite expects the username 'seeded', and this is not it
        var seeded = crud.create(SEEDED.replace("\"seeded\"", "\"someone else\""));

        // when
        var result = run(
                "suites/read-one-user.yaml", Map.of("userId", seeded.get("id").asString()));

        // then — a failed assertion is data on the result, and the request still completed
        assertThat(result.failed()).isTrue();
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(assertionResultsOf(result))
                .filteredOn(assertion -> !assertion.passed())
                .extracting(AssertionResult::message)
                .containsExactly("the username must come back as it went in");
    }
}

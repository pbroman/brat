package dev.pbroman.brat.core.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodyFileChecksTest {

    @TempDir
    Path dir;

    private static Request requestWithBody(String name, Map<String, String> body) {
        var definition = new HttpRequestDefinition("http://url", "GET", null, body, null, null);
        return new Request(name, null, null, null, null, null, definition, null, null);
    }

    private static TestSuite suite(List<Request> requests, List<TestSuite> subSuites) {
        return new TestSuite("suite", null, null, null, null, null, null, null, null, requests, subSuites);
    }

    @Test
    void check_passesWhenEveryTokenFreeBodyFileExists() throws IOException {
        // given
        var file = dir.resolve("order.json");
        Files.writeString(file, "{}");
        var suite = suite(List.of(requestWithBody("one", Map.of(FILE_BODY, "file:" + file))), null);

        // when / then
        assertThatCode(() -> BodyFileChecks.check(suite, null)).doesNotThrowAnyException();
    }

    @Test
    void check_throwsNamingEveryMissingFile() {
        // given - one launch per typo is the cost of reporting only the first
        var suite = suite(
                List.of(
                        requestWithBody("one", Map.of(FILE_BODY, "file:" + dir.resolve("first.json"))),
                        requestWithBody("two", Map.of(FILE_BODY, "file:" + dir.resolve("second.json")))),
                null);

        // then
        assertThatThrownBy(() -> BodyFileChecks.check(suite, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("first.json")
                .hasMessageContaining("second.json");
    }

    @Test
    void check_namesTheRequestThatDeclaredTheMissingFile() {
        // given
        var suite = suite(
                List.of(requestWithBody("create an order", Map.of(FILE_BODY, "file:" + dir.resolve("x.json")))), null);

        // then - the path of the request, so an author can find the key that named it
        assertThatThrownBy(() -> BodyFileChecks.check(suite, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("create an order");
    }

    @Test
    void check_skipsAPathHoldingAToken() {
        // given - it may name a different file per environment, so its unresolved text proves nothing
        var suite =
                suite(List.of(requestWithBody("one", Map.of(FILE_BODY, "file:" + dir + "/${env.stage}.json"))), null);

        // when / then
        assertThatCode(() -> BodyFileChecks.check(suite, null)).doesNotThrowAnyException();
    }

    @Test
    void check_walksSubSuites() {
        // given
        var nested =
                suite(List.of(requestWithBody("deep", Map.of(FILE_BODY, "file:" + dir.resolve("deep.json")))), null);
        var root = suite(null, List.of(nested));

        // then
        assertThatThrownBy(() -> BodyFileChecks.check(root, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("deep.json");
    }

    @Test
    void check_ignoresRequestsWithNoFileBody() {
        // given
        var suite = suite(
                List.of(
                        requestWithBody("raw", Map.of(RAW_BODY, "{}")),
                        requestWithBody("none", null),
                        new Request("no definition", null, null, null, null, null, null, null, null)),
                null);

        // when / then
        assertThatCode(() -> BodyFileChecks.check(suite, null)).doesNotThrowAnyException();
    }

    @Test
    void check_resolvesABarePathAgainstTheSuiteLocation() throws IOException {
        // given - the suite sits beside the body file and names it without a prefix
        var file = dir.resolve("order.json");
        Files.writeString(file, "{}");
        var suite = suite(List.of(requestWithBody("one", Map.of(FILE_BODY, "order.json"))), null);

        // when / then
        assertThatCode(() -> BodyFileChecks.check(suite, "file:" + dir.resolve("orders.yaml")))
                .doesNotThrowAnyException();
    }

    @Test
    void check_throwsWhenABarePathHasNoSuiteLocation() {
        // given - a null suite location is legal until something needs resolving against it
        var suite = suite(List.of(requestWithBody("one", Map.of(FILE_BODY, "order.json"))), null);

        // then
        assertThatThrownBy(() -> BodyFileChecks.check(suite, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("order.json");
    }

    @Test
    void check_throwsWhenTheSuiteIsNull() {
        assertThatThrownBy(() -> BodyFileChecks.check(null, null)).isInstanceOf(BratException.class);
    }
}

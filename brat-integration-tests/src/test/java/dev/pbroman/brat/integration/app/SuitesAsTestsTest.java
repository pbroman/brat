package dev.pbroman.brat.integration.app;

import java.util.stream.Stream;

import dev.pbroman.brat.junit.BratTests;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Suites run as JUnit tests of the app the test itself starts — exactly what a user writes: the server
 * started by {@code @SpringBootTest} on a free port, its url handed over, every request a test.
 *
 * <p>Each factory passes only if every request in its suite passes, so this class going green is the
 * end-to-end claim. {@code JUnitBridgeTest} runs it once more through JUnit's testkit to check what JUnit
 * reported, test by test.
 */
@SpringBootTest(classes = CrudApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class SuitesAsTestsTest {

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** A tree two levels deep: setup, main, two scenarios and teardown, all green. */
    @TestFactory
    Stream<DynamicNode> order() {
        // found from this module's directory under Maven, and from the repository root in an IDE
        return BratTests.suite("brat-integration-tests/src/test/resources/suites/tree/order.brat.yaml")
                .env("baseUrl", baseUrl())
                .env("tag", "junit")
                .tests();
    }

    /** The login steps aside for a token the test hands over, and the app accepts it. */
    @TestFactory
    Stream<DynamicNode> handedOverToken() {
        return BratTests.suite("brat-integration-tests/src/test/resources/suites/auth/handed-over-token.brat.yaml")
                .env("baseUrl", baseUrl())
                .param("vars.accessToken", "admin-token")
                .tests();
    }
}

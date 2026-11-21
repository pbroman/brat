package dev.pbroman.brat.openapi;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.FlatYamlLoader;
import dev.pbroman.brat.openapi.Scaffold.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mapping from a spec to a scaffold. Each test reads the emitted text back as YAML, so it asserts
 * what a suite means rather than how it is laid out; {@link ImportedSuitesTest} checks that it loads.
 */
class OpenApiImporterTest {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String SOURCE = "orders-openapi.yaml";

    private static final String HEAD = """
            openapi: 3.0.3
            info:
              title: Orders API
            servers:
              - url: http://localhost:8080
                description: local
            """;

    private static Scaffold scaffold(String spec) {
        return OpenApiImporter.scaffold(spec, SOURCE, ImportOptions.defaults());
    }

    /** A scaffold of {@link #HEAD} followed by {@code paths}. */
    private static Scaffold withPaths(String paths) {
        return scaffold(HEAD + "paths:\n" + paths.indent(2));
    }

    private static String suiteText(Scaffold scaffold) {
        return scaffold.files().get(scaffold.suiteFile());
    }

    private static JsonNode suite(Scaffold scaffold) {
        return YAML.readTree(suiteText(scaffold));
    }

    private static JsonNode onlyRequest(Scaffold scaffold) {
        var requests = suite(scaffold).get("requests");
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    /** Every request of the suite in the order written: the top's, then each sub-suite's. */
    private static List<JsonNode> allRequests(Scaffold scaffold) {
        var suite = suite(scaffold);
        var result = new ArrayList<JsonNode>();
        if (suite.has("requests")) {
            suite.get("requests").forEach(result::add);
        }
        if (suite.has("subSuites")) {
            for (var subSuite : suite.get("subSuites")) {
                subSuite.get("requests").forEach(result::add);
            }
        }
        return result;
    }

    private static List<String> names(JsonNode nodes) {
        var names = new ArrayList<String>();
        for (var node : nodes) {
            names.add(node.get("name").asString());
        }
        return names;
    }

    private static List<String> notesIn(Scaffold scaffold, String file) {
        return scaffold.notes().stream()
                .filter(note -> note.file().equals(file))
                .map(Note::message)
                .toList();
    }

    /** The line of the suite text holding {@code fragment}. */
    private static String lineWith(Scaffold scaffold, String fragment) {
        return suiteText(scaffold)
                .lines()
                .filter(line -> line.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line holds " + fragment));
    }

    private static String constant(Scaffold scaffold, String name) {
        var constants = suite(scaffold).get("constants");
        assertThat(constants).as("constants").isNotNull();
        var value = constants.get(name);
        assertThat(value).as("constants." + name).isNotNull();
        assertThat(value.isString()).as("constants." + name + " is text").isTrue();
        return value.asString();
    }

    private static String raw(JsonNode request) {
        return request.get("requestDefinition").get("body").get("raw").asString();
    }

    private static final String GET_HEALTH = """
            /health:
              get:
                summary: health check
                responses:
                  "200": { description: up }
            """;

    // --- the suite ---

    @Test
    void scaffold_namesTheSuiteFileAndDirectoryAfterTheTitle() {
        // when
        var scaffold = withPaths(GET_HEALTH);

        // then
        assertThat(scaffold.directory()).isEqualTo("orders-api");
        assertThat(scaffold.suiteFile()).isEqualTo("orders-api.brat.yaml");
        assertThat(scaffold.files().keySet()).first().isEqualTo("orders-api.brat.yaml");
        assertThat(suite(scaffold).get("name").asString()).isEqualTo("Orders API");
    }

    @Test
    void scaffold_usesImportedWithoutATitle() {
        // when
        var scaffold = scaffold("openapi: 3.1.0\n");

        // then
        assertThat(scaffold.directory()).isEqualTo("imported");
        assertThat(scaffold.suiteFile()).isEqualTo("imported.brat.yaml");
        assertThat(suite(scaffold).get("name").asString()).isEqualTo("imported");
    }

    @Test
    void scaffold_usesImportedWhenNothingOfTheTitleIsLeftForAFileName() {
        // when
        var scaffold = scaffold("openapi: 3.1.0\ninfo:\n  title: \"???\"\n");

        // then
        assertThat(scaffold.directory()).isEqualTo("imported");
        assertThat(suite(scaffold).get("name").asString()).isEqualTo("???");
    }

    @Test
    void scaffold_replacesSlashesInTheSuiteName() {
        // when
        var scaffold = scaffold("openapi: 3.1.0\ninfo:\n  title: Orders/Billing API\n");

        // then
        assertThat(suite(scaffold).get("name").asString()).isEqualTo("Orders Billing API");
    }

    @Test
    void scaffold_startsWithACommentNamingTheSourceAndTheTitle() {
        // when
        var scaffold = withPaths(GET_HEALTH);

        // then
        var first = suiteText(scaffold).lines().findFirst().orElseThrow();
        assertThat(first).startsWith("#").contains(SOURCE).contains("Orders API");
    }

    @Test
    void scaffold_namesNoSourceWhenThereIsNone() {
        // when
        var scaffold = OpenApiImporter.scaffold(HEAD, null, ImportOptions.defaults());

        // then
        var first = suiteText(scaffold).lines().findFirst().orElseThrow();
        assertThat(first).startsWith("#").contains("Orders API").doesNotContain("null");
    }

    @Test
    void scaffold_writesALineBreakInTheSourceNameAsASpace() {
        // when
        var scaffold = OpenApiImporter.scaffold(HEAD, "orders\nopenapi.yaml", ImportOptions.defaults());

        // then
        var first = suiteText(scaffold).lines().findFirst().orElseThrow();
        assertThat(first).contains("orders openapi.yaml");
        assertThat(suite(scaffold).get("name").asString()).isEqualTo("Orders API");
    }

    @Test
    void scaffold_endsEveryFileInANewline() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: {"a": 1, "b": 2, "c": 3, "d": 4, "e": 5, "f": 6, "g": 7, "h": 8, "i": 9, "j": 10}
                    responses:
                      "201": { description: created }
                """);

        // then
        assertThat(scaffold.files()).hasSize(3);
        assertThat(scaffold.files().values())
                .allSatisfy(content -> assertThat(content).endsWith("\n"));
    }

    // --- requests ---

    @Test
    void scaffold_writesOneRequestPerOperationInSpecOrder() {
        // when
        // when - each path its own first segment, so no two are grouped
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                /order-list:
                  get:
                    summary: list orders
                /health:
                  get:
                    summary: health check
                """);

        // then
        assertThat(names(suite(scaffold).get("requests")))
                .containsExactly("create an order", "list orders", "health check");
        assertThat(scaffold.requests()).isEqualTo(3);
        assertThat(scaffold.subSuites()).isZero();
    }

    @Test
    void scaffold_namesARequestBySummaryThenOperationIdThenMethodAndPath() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    operationId: createOrder
                  get:
                    summary: "  "
                    operationId: listOrders
                /orders/{orderId}:
                  get: {}
                """);

        // then - untagged and sharing a first segment, they are grouped under it
        assertThat(names(suite(scaffold).get("subSuites").get(0).get("requests")))
                .containsExactly("create an order", "listOrders", "GET orders {orderId}");
    }

    @Test
    void scaffold_putsTaggedOperationsInASubSuitePerFirstTag() {
        // when
        var scaffold = withPaths("""
                /health:
                  get:
                    summary: health check
                /orders:
                  post:
                    summary: create an order
                    tags: [orders, write]
                /orders/import:
                  post:
                    summary: import orders
                    tags: [bulk]
                /orders/{orderId}:
                  get:
                    summary: read an order
                    tags: [orders]
                """);

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("health check");
        assertThat(names(suite.get("subSuites"))).containsExactly("orders", "bulk");
        assertThat(names(suite.get("subSuites").get(0).get("requests")))
                .containsExactly("create an order", "read an order");
        assertThat(names(suite.get("subSuites").get(1).get("requests"))).containsExactly("import orders");
        assertThat(scaffold.requests()).isEqualTo(4);
        assertThat(scaffold.subSuites()).isEqualTo(2);
    }

    @Test
    void scaffold_writesNoTopLevelRequestsWhenEveryOperationIsTagged() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    tags: [orders]
                """);

        // then
        var suite = suite(scaffold);
        assertThat(suite.has("requests")).isFalse();
        assertThat(names(suite.get("subSuites"))).containsExactly("orders");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"/\"", "\" \""})
    void scaffold_keepsAnOperationAtTheTop_whenItsFirstTagLeavesNoName(String tag) {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    tags: [%s, orders]
                """.formatted(tag));

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("list orders");
        assertThat(suite.has("subSuites")).isFalse();
    }

    @Test
    void scaffold_makesATagIntoANodeName() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    tags: ["orders/v2"]
                """);

        // then
        assertThat(names(suite(scaffold).get("subSuites"))).containsExactly("orders v2");
    }

    @Test
    void scaffold_numbersSiblingsThatShareAName() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: orders
                  post:
                    summary: orders
                /orders/{orderId}:
                  get:
                    summary: read
                    tags: [orders]
                  delete:
                    summary: read
                    tags: [orders]
                """);

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("orders", "orders 2");
        assertThat(names(suite.get("subSuites"))).containsExactly("orders 3");
        assertThat(names(suite.get("subSuites").get(0).get("requests"))).containsExactly("read", "read 2");
    }

    @Test
    void scaffold_writesTheMethodUnlessItIsGet() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                  delete:
                    summary: delete all orders
                """);

        // then
        var requests = allRequests(scaffold);
        assertThat(requests.get(0).get("requestDefinition").has("method")).isFalse();
        assertThat(requests.get(1).get("requestDefinition").get("method").asString())
                .isEqualTo("DELETE");
    }

    // --- urls, headers and constants ---

    @Test
    void scaffold_buildsTheUrlFromBaseUrlPathAndRequiredQueryParameters() {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}/lines:
                  get:
                    summary: read lines
                    parameters:
                      - { name: orderId, in: path, example: "4711" }
                      - { name: status, in: query, required: true, example: OPEN }
                      - { name: page, in: query, required: true, example: 1 }
                      - { name: limit, in: query, required: false }
                """);

        // then
        var url = onlyRequest(scaffold).get("requestDefinition").get("url").asString();
        assertThat(url)
                .isEqualTo("${env.baseUrl}/orders/${constants.orderId}/lines"
                        + "?status=${constants.status}&page=${constants.page}");
        assertThat(constant(scaffold, "orderId")).isEqualTo("4711");
        assertThat(constant(scaffold, "status")).isEqualTo("OPEN");
        assertThat(constant(scaffold, "page")).isEqualTo("1");
    }

    @Test
    void scaffold_namesTheParametersItDoesNotSendInACommentOnTheUrl() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: limit, in: query }
                      - { name: X-Trace, in: header }
                """);

        // then
        assertThat(lineWith(scaffold, "url:")).contains("#").contains("limit").contains("X-Trace");
        assertThat(suite(scaffold).has("constants")).isFalse();
    }

    @Test
    void scaffold_sendsARequiredHeaderParameterFromAConstant() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: X-Tenant, in: header, required: true, example: acme }
                """);

        // then
        var headers = onlyRequest(scaffold).get("requestDefinition").get("headers");
        assertThat(headers.get("X-Tenant").asString()).isEqualTo("${constants.X-Tenant}");
        assertThat(constant(scaffold, "X-Tenant")).isEqualTo("acme");
    }

    @Test
    void scaffold_ignoresAcceptContentTypeAndAuthorizationHeaderParameters() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: accept, in: header, required: true, example: application/json }
                      - { name: Content-Type, in: header, required: true, example: application/json }
                      - { name: AUTHORIZATION, in: header, required: true, example: secret }
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("headers")).isFalse();
        assertThat(suite(scaffold).has("constants")).isFalse();
        assertThat(suiteText(scaffold)).doesNotContain("secret");
    }

    @Test
    void scaffold_notesARequiredCookieParameterItDoesNotSend() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: session, in: cookie, required: true, example: abc }
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("headers")).isFalse();
        assertThat(suite(scaffold).has("constants")).isFalse();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("session"));
    }

    @Test
    void scaffold_sharesOneConstantPerParameterNameInOrderOfFirstUse() {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}:
                  get:
                    summary: read an order
                    parameters:
                      - { name: orderId, in: path, example: "4711" }
                      - { name: status, in: query, required: true, example: OPEN }
                    responses:
                      "200": { description: the order }
                  delete:
                    summary: delete an order
                    parameters:
                      - { name: orderId, in: path, example: "4711" }
                    responses:
                      "204": { description: deleted }
                """);

        // then
        var constants = suite(scaffold).get("constants");
        assertThat(constants.propertyNames()).containsExactly("orderId", "status");
        assertThat(notesIn(scaffold, scaffold.suiteFile())).isEmpty();
    }

    @Test
    void scaffold_takesTheFirstExampleAndNotesTheRequestsThatDisagree() {
        // when
        var scaffold = withPaths("""
                /orders/{id}:
                  get:
                    summary: read an order
                    parameters:
                      - { name: id, in: path, example: "4711" }
                /customers/{id}:
                  get:
                    summary: read a customer
                    parameters:
                      - { name: id, in: path, example: C-17 }
                """);

        // then
        assertThat(constant(scaffold, "id")).isEqualTo("4711");
        assertThat(suiteText(scaffold)).contains("read a customer").contains("C-17");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("id").contains("read a customer"));
    }

    @Test
    void scaffold_commentsAConstantOutAndNotesIt_whenThereIsNoExample() {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}:
                  get:
                    summary: read an order
                """);

        // then - the url still sends it, so the run names it rather than sending a blank
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("url").asString())
                .isEqualTo("${env.baseUrl}/orders/${constants.orderId}");
        assertThat(suite(scaffold).has("constants")).isFalse();
        assertThat(lineWith(scaffold, "orderId:").strip()).startsWith("# orderId:");
        assertThat(lineWith(scaffold, "constants:").strip()).startsWith("# constants:");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("orderId").contains("-p constants.orderId="));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"${vars.id}\"", "[1, 2]", "{ a: 1 }"})
    void scaffold_commentsAConstantOutAndNotesIt_whenTheExampleIsNotASafeSingleValue(String example) {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}:
                  get:
                    summary: read an order
                    parameters:
                      - name: orderId
                        in: path
                        example: %s
                """.formatted(example));

        // then
        assertThat(suite(scaffold).has("constants")).isFalse();
        assertThat(lineWith(scaffold, "orderId:").strip()).startsWith("# orderId:");
        assertThat(suiteText(scaffold)).doesNotContain("${vars.id}");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("orderId"));
    }

    @Test
    void scaffold_percentEncodesAPathOrQueryConstantAndCommentsTheExample() {
        // when
        var scaffold = withPaths("""
                /files/{name}:
                  get:
                    summary: read a file
                    parameters:
                      - { name: name, in: path, example: "two words/ä" }
                      - { name: q, in: query, required: true, example: "a&b=c" }
                """);

        // then
        assertThat(constant(scaffold, "name")).isEqualTo("two%20words%2F%C3%A4");
        assertThat(constant(scaffold, "q")).isEqualTo("a%26b%3Dc");
        assertThat(lineWith(scaffold, "two%20words")).contains("#").contains("two words/ä");
    }

    @Test
    void scaffold_writesAConstantAsItsFirstUseSendsIt() {
        // when
        var scaffold = withPaths("""
                /files:
                  get:
                    summary: list files
                    parameters:
                      - { name: name, in: header, required: true, example: "two words" }
                /files/{name}:
                  get:
                    summary: read a file
                    parameters:
                      - { name: name, in: path, example: "two words" }
                """);

        // then
        assertThat(constant(scaffold, "name")).isEqualTo("two words");
    }

    @Test
    void scaffold_writesAHeaderConstantAsItIs() {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: X-Tenant, in: header, required: true, example: "acme corp/eu" }
                """);

        // then
        assertThat(constant(scaffold, "X-Tenant")).isEqualTo("acme corp/eu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "\"4711\"", "\"a: b\"", "\"#x\"", "\"null\"", "\"~\"", "\"-1\""})
    void scaffold_keepsAConstantText_whereYamlWouldReadItAsSomethingElse(String example) {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - name: X-Value
                        in: header
                        required: true
                        example: %s
                """.formatted(example));

        // then
        var expected = YAML.readTree("v: " + example).get("v").asString();
        assertThat(constant(scaffold, "X-Value")).isEqualTo(expected);
    }

    // --- bodies ---

    @Test
    void scaffold_writesAJsonExampleInlineAsPrintedJson() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: {"item":"book","quantity":2,"tags":[],"meta":{}}
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.get("headers").get("Content-Type").asString()).isEqualTo("application/json");
        assertThat(raw(onlyRequest(scaffold)).strip()).isEqualTo("""
                        {
                          "item": "book",
                          "quantity": 2,
                          "tags": [],
                          "meta": {}
                        }""");
    }

    @Test
    void scaffold_readsAnExampleGivenAsTextAsJson() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: '{"item":"book"}'
                """);

        // then
        assertThat(raw(onlyRequest(scaffold)).strip()).isEqualTo("{\n  \"item\": \"book\"\n}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"'item=book'", "'.'", "'42'", "'\"quoted\"'"})
    void scaffold_writesTextThatIsNotAJsonObjectOrListAsAJsonString(String example) {
        // when - the value is the text; its JSON is a string
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: %s
                """.formatted(example));

        // then
        var raw = raw(onlyRequest(scaffold)).strip();
        assertThat(JSON.readTree(raw).isString()).as(raw).isTrue();
        assertThat(JSON.readTree(raw).asString())
                .isEqualTo(YAML.readTree("v: " + example).get("v").asString());
    }

    @Test
    void scaffold_writesAScalarSchemaExampleAsJson() {
        // when - tic-tac-toe's mark: a string schema whose example is "."
        var scaffold = withPaths("""
                /board:
                  put:
                    summary: mark
                    requestBody:
                      content:
                        application/json:
                          schema: { type: string, example: . }
                """);

        // then
        assertThat(raw(onlyRequest(scaffold)).strip()).isEqualTo("\".\"");
    }

    @Test
    void scaffold_writesASerializedValueAsItIsWhateverItsLength() {
        // when
        var scaffold = OpenApiImporter.scaffold(HEAD.replace("3.0.3", "3.2.0") + """
                        paths:
                          /orders:
                            post:
                              summary: create an order
                              requestBody:
                                content:
                                  application/json:
                                    examples:
                                      one:
                                        serializedValue: '{"item":"book","quantity":2}'
                        """, SOURCE, new ImportOptions(0));

        // then - not re-printed, and inline even with a limit of zero lines
        assertThat(raw(onlyRequest(scaffold))).isEqualTo("{\"item\":\"book\",\"quantity\":2}");
        assertThat(lineWith(scaffold, "raw:")).contains("#");
    }

    @Test
    void scaffold_prefersAJsonMediaTypeIncludingPlusJson() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema: { properties: { item: {} } }
                        application/vnd.orders+json:
                          example: {"item":"book"}
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.get("headers").get("Content-Type").asString()).isEqualTo("application/vnd.orders+json");
        assertThat(definition.get("body").has("raw")).isTrue();
    }

    @Test
    void scaffold_keepsABodyOfExactlyTheLimitInline() {
        // given
        var spec = HEAD + """
                paths:
                  /orders:
                    post:
                      summary: create an order
                      requestBody:
                        content:
                          application/json:
                            example: {"a": 1, "b": 2, "c": 3}
                """;

        // when
        var scaffold = OpenApiImporter.scaffold(spec, SOURCE, new ImportOptions(5));

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("body").has("raw"))
                .isTrue();
        assertThat(scaffold.files().keySet()).noneMatch(path -> path.startsWith("bodies/"));
    }

    @Test
    void scaffold_writesALongerBodyToAFileCountingThePrintedLines() {
        // given
        var spec = HEAD + """
                paths:
                  /orders/import:
                    post:
                      summary: import orders
                      requestBody:
                        content:
                          application/json:
                            example: {"a": 1, "b": 2, "c": 3, "d": 4}
                """;

        // when
        var scaffold = OpenApiImporter.scaffold(spec, SOURCE, new ImportOptions(5));

        // then
        var body = onlyRequest(scaffold).get("requestDefinition").get("body");
        assertThat(body.get("file").asString()).isEqualTo("bodies/import-orders.json");
        assertThat(JSON.readTree(scaffold.files().get("bodies/import-orders.json")))
                .isEqualTo(JSON.readTree("{\"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4}"));
        assertThat(lineWith(scaffold, "file:")).contains("#").contains("6").contains("5");
        assertThat(List.copyOf(scaffold.files().keySet()))
                .containsExactly("orders-api.brat.yaml", "bodies/import-orders.json", "local/env.yaml");
    }

    @Test
    void scaffold_sendsEveryJsonBodyToAFileWithALimitOfZero() {
        // given
        var spec = HEAD + """
                paths:
                  /orders:
                    post:
                      summary: create an order
                      requestBody:
                        content:
                          application/json:
                            example: 1
                """;

        // when
        var scaffold = OpenApiImporter.scaffold(spec, SOURCE, new ImportOptions(0));

        // then
        assertThat(onlyRequest(scaffold)
                        .get("requestDefinition")
                        .get("body")
                        .get("file")
                        .asString())
                .isEqualTo("bodies/create-an-order.json");
    }

    @Test
    void scaffold_numbersBodyFilesThatShareANameAcrossTheSuite() {
        // given
        var spec = HEAD + """
                paths:
                  /a:
                    post:
                      summary: create
                      requestBody:
                        content:
                          application/json:
                            example: 1
                  /b:
                    post:
                      summary: Create
                      tags: [b]
                      requestBody:
                        content:
                          application/json:
                            example: 2
                  /c:
                    post:
                      summary: "???"
                      requestBody:
                        content:
                          application/json:
                            example: 3
                """;

        // when
        var scaffold = OpenApiImporter.scaffold(spec, SOURCE, new ImportOptions(0));

        // then
        assertThat(scaffold.files().keySet())
                .contains("bodies/create.json", "bodies/create-2.json", "bodies/body.json");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "          example: {\"id\": \"${vars.id}\"}\n", "          example: 'id=${vars.id}'\n"})
    void scaffold_leavesAJsonBodyEmptyAndNotesIt_withoutAUsableExample(String example) {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                """ + example + "          schema: { type: object }\n");

        // then
        assertThat(raw(onlyRequest(scaffold))).isEmpty();
        assertThat(suiteText(scaffold)).doesNotContain("${vars.id}");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("create an order"));
    }

    @Test
    void scaffold_writesAFormBodyFromTheSchemaPropertiesAndTheExample() {
        // when
        var scaffold = withPaths("""
                /login:
                  post:
                    summary: log in
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema:
                            required: [username, password, remember]
                            properties:
                              username: {}
                              password: {}
                              remember: {}
                          example: { username: alice, remember: true }
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("headers")).isFalse();
        var form = definition.get("body").get("form");
        assertThat(form.propertyNames()).containsExactly("username", "password", "remember");
        assertThat(form.get("username").asString()).isEqualTo("alice");
        assertThat(form.get("password").asString()).isEmpty();
        assertThat(form.get("remember").isString()).isTrue();
        assertThat(form.get("remember").asString()).isEqualTo("true");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("password"));
    }

    @Test
    void scaffold_leavesAFormEntryEmptyAndNotesIt_whenItsExampleHoldsAPlaceholder() {
        // when
        var scaffold = withPaths("""
                /login:
                  post:
                    summary: log in
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema: { required: [token], properties: { token: {} } }
                          example: { token: "${vars.token}" }
                """);

        // then
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.get("token").asString()).isEmpty();
        assertThat(suiteText(scaffold)).doesNotContain("${vars.token}");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/x-www-form-urlencoded; charset=UTF-8", "Application/X-WWW-Form-Urlencoded"})
    void scaffold_recognisesAFormWhateverItsCaseAndParameters(String type) {
        // when
        var scaffold = withPaths("""
                /login:
                  post:
                    summary: log in
                    requestBody:
                      content:
                        text/plain: {}
                        "%s":
                          schema: { required: [user], properties: { user: {} } }
                """.formatted(type));

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("headers")).isFalse();
        assertThat(definition.get("body").get("form").propertyNames()).containsExactly("user");
    }

    @Test
    void scaffold_sendsNoBodyAndNotesIt_forAFormWithoutPropertiesOrExample() {
        // when - Stripe's empty forms
        var scaffold = withPaths("""
                /login:
                  post:
                    summary: log in
                    requestBody:
                      content:
                        application/x-www-form-urlencoded: {}
                """);

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").has("body")).isFalse();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("log in").contains("empty form"));
    }

    @Test
    void scaffold_keepsAFormBodyInlineWhateverTheLimit() {
        // given
        var spec = HEAD + """
                paths:
                  /login:
                    post:
                      summary: log in
                      requestBody:
                        content:
                          application/x-www-form-urlencoded:
                            schema: { required: [a, b, c], properties: { a: {}, b: {}, c: {} } }
                """;

        // when
        var scaffold = OpenApiImporter.scaffold(spec, SOURCE, new ImportOptions(0));

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("body").has("form"))
                .isTrue();
        assertThat(scaffold.files().keySet()).noneMatch(path -> path.startsWith("bodies/"));
    }

    @Test
    void scaffold_writesAnEmptyRawBodyAndNotesIt_forAnyOtherMediaType() {
        // when
        var scaffold = withPaths("""
                /documents:
                  put:
                    summary: upload a document
                    requestBody:
                      content:
                        application/pdf:
                          example: "%PDF-1.7"
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.get("headers").get("Content-Type").asString()).isEqualTo("application/pdf");
        assertThat(raw(onlyRequest(scaffold))).isEmpty();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("upload a document"));
    }

    @Test
    void scaffold_writesNoBodyForAnOperationWithout() {
        // when
        var scaffold = withPaths(GET_HEALTH);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("body")).isFalse();
        assertThat(definition.has("headers")).isFalse();
    }

    // --- the assertion ---

    @Test
    void scaffold_assertsTheLowestDocumentedSuccessCode() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    responses:
                      "400": { description: invalid }
                      "201": { description: created }
                      "200": { description: existing }
                      default: { description: error }
                """);

        // then
        var assertions = onlyRequest(scaffold).get("responseActions").get("assertions");
        assertThat(assertions).hasSize(1);
        var assertion = assertions.get(0);
        assertThat(assertion.get("a").asString()).isEqualTo("${response.statusCode}");
        assertThat(assertion.get("func").asString()).isEqualTo("isEqualTo");
        assertThat(assertion.get("b").asInt()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "responses: { \"2XX\": { description: ok } }",
                "responses: { \"404\": { description: gone } }",
                "responses: {}",
                "operationId: listOrders"
            })
    void scaffold_writesNoAssertionAndNotesIt_withoutADocumentedSuccessCode(String responses) {
        // when
        var scaffold = withPaths("""
                /orders:
                  get:
                    summary: list orders
                    %s
                """.formatted(responses));

        // then
        assertThat(onlyRequest(scaffold).has("responseActions")).isFalse();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("list orders"));
    }

    // --- environments ---

    @Test
    void scaffold_writesAnEnvironmentPerServerNamedByItsDescription() {
        // given
        var spec = """
                openapi: 3.0.3
                info:
                  title: Orders API
                servers:
                  - url: http://localhost:8080/
                    description: local
                  - url: https://orders.staging.example.com/api/v1
                    description: Staging Server
                """;

        // when
        var scaffold = scaffold(spec);

        // then
        assertThat(scaffold.environments()).containsExactly("local", "staging-server");
        assertThat(FlatYamlLoader.load(scaffold.files().get("local/env.yaml")))
                .containsEntry("baseUrl", "http://localhost:8080");
        assertThat(FlatYamlLoader.load(scaffold.files().get("staging-server/env.yaml")))
                .containsEntry("baseUrl", "https://orders.staging.example.com/api/v1");
        assertThat(List.copyOf(scaffold.files().keySet()))
                .containsExactly("orders-api.brat.yaml", "local/env.yaml", "staging-server/env.yaml");
        assertThat(scaffold.notes()).isEmpty();
    }

    @Test
    void scaffold_namesEnvironmentsByPositionWithoutADescriptionAndNumbersTakenNames() {
        // given
        var spec = """
                openapi: 3.0.3
                servers:
                  - url: http://a.example.com
                  - url: http://b.example.com
                    description: "!!!"
                  - url: http://c.example.com
                    description: bodies
                  - url: http://d.example.com
                    description: Server 1
                """;

        // when
        var scaffold = scaffold(spec);

        // then
        assertThat(scaffold.environments()).containsExactly("server-1", "server-2", "bodies-2", "server-1-2");
    }

    @Test
    void scaffold_replacesServerVariablesByTheirDefaults() {
        // given
        var spec = """
                openapi: 3.0.3
                servers:
                  - url: https://{region}.example.com:{port}
                    description: prod
                    variables:
                      region: { default: eu }
                      port: { default: "8443" }
                """;

        // when
        var scaffold = scaffold(spec);

        // then
        assertThat(FlatYamlLoader.load(scaffold.files().get("prod/env.yaml")))
                .containsEntry("baseUrl", "https://eu.example.com:8443");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "https://{region}.example.com"})
    void scaffold_leavesBaseUrlEmptyAndNotesIt_forAServerUrlItCannotUse(String url) {
        // given
        var spec = """
                openapi: 3.0.3
                servers:
                  - url: "%s"
                    description: prod
                """.formatted(url);

        // when
        var scaffold = scaffold(spec);

        // then
        var envText = scaffold.files().get("prod/env.yaml");
        assertThat(FlatYamlLoader.load(envText)).containsEntry("baseUrl", "");
        assertThat(envText).contains("#").contains(url);
        assertThat(notesIn(scaffold, "prod/env.yaml"))
                .anySatisfy(note -> assertThat(note).contains("baseUrl"));
    }

    @Test
    void scaffold_writesOneEmptyLocalEnvironmentWithoutServers() {
        // when
        var scaffold = scaffold("openapi: 3.1.0\n");

        // then
        assertThat(scaffold.environments()).containsExactly("local");
        assertThat(FlatYamlLoader.load(scaffold.files().get("local/env.yaml"))).containsEntry("baseUrl", "");
        assertThat(notesIn(scaffold, "local/env.yaml"))
                .anySatisfy(note -> assertThat(note).contains("baseUrl"));
    }

    // --- notes ---

    @Test
    void scaffold_ordersNotesByFileAndPointsEachAtAFileItWrites() {
        // given
        var spec = """
                openapi: 3.0.3
                info:
                  title: Orders API
                servers:
                  - url: /api
                    description: local
                paths:
                  /orders/{orderId}:
                    get:
                      summary: read an order
                """;

        // when
        var scaffold = scaffold(spec);

        // then
        assertThat(scaffold.notes()).extracting(Note::file).allMatch(scaffold.files()::containsKey);
        assertThat(scaffold.notes()).extracting(Note::file).first().isEqualTo(scaffold.suiteFile());
        assertThat(scaffold.notes()).extracting(Note::file).last().isEqualTo("local/env.yaml");
    }

    // --- refusals ---

    @Test
    void scaffold_refusesNullContent() {
        // when / then
        assertThatThrownBy(() -> OpenApiImporter.scaffold(null, SOURCE, ImportOptions.defaults()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void scaffold_refusesNullOptions() {
        // when / then
        assertThatThrownBy(() -> OpenApiImporter.scaffold(HEAD, SOURCE, null)).isInstanceOf(BratException.class);
    }

    @Test
    void scaffold_passesOnWhatTheReaderRefuses() {
        // when / then
        assertThatThrownBy(() -> OpenApiImporter.scaffold("swagger: \"2.0\"\n", SOURCE, ImportOptions.defaults()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Swagger 2");
    }

    // --- constants by identity ---

    private static final String SPOTIFY_LIKE = """
            /search:
              get:
                summary: search
                parameters:
                  - name: type
                    in: query
                    required: true
                    schema:
                      type: array
                      items: { type: string, enum: [album, artist, playlist, track, show, episode] }
            /me/top/{type}:
              get:
                summary: top items
                parameters:
                  - { name: type, in: path, schema: { type: string, enum: [artists, tracks] } }
            /me/following:
              get:
                summary: followed
                parameters:
                  - { name: type, in: query, required: true, schema: { type: string, enum: [artist] } }
              put:
                summary: follow
                parameters:
                  - { name: type, in: query, required: true, example: artist, schema: { type: string, enum: [artist] } }
            """;

    @Test
    void scaffold_sharesAConstantOnlyBetweenParametersTheSpecMakesTheSame() {
        // when
        var scaffold = withPaths(SPOTIFY_LIKE);

        // then - three meanings of "type", three constants; the two following ones share one
        var constants = suite(scaffold).get("constants");
        assertThat(constants.propertyNames()).containsExactly("type", "type_top", "type_following");
        var urls = new ArrayList<String>();
        for (var request : allRequests(scaffold)) {
            urls.add(request.get("requestDefinition").get("url").asString());
        }
        assertThat(urls)
                .containsExactly(
                        "${env.baseUrl}/search?type=${constants.type}",
                        "${env.baseUrl}/me/top/${constants.type_top}",
                        "${env.baseUrl}/me/following?type=${constants.type_following}",
                        "${env.baseUrl}/me/following?type=${constants.type_following}");
    }

    @Test
    void scaffold_takesTheFirstUsableExampleAmongTheOperationsAConstantServes() {
        // when - "followed" has none, "follow" has one
        var scaffold = withPaths(SPOTIFY_LIKE);

        // then
        assertThat(constant(scaffold, "type_following")).isEqualTo("artist");
        assertThat(lineWith(scaffold, "type_following:")).contains("example from the spec");
    }

    @Test
    void scaffold_takesTheFirstEnumValueWithoutAnExampleNamingAtMostFive() {
        // when
        var scaffold = withPaths(SPOTIFY_LIKE);

        // then - an array's items' enum counts as its own
        assertThat(constant(scaffold, "type")).isEqualTo("album");
        assertThat(lineWith(scaffold, "  type:"))
                .contains("album, artist, playlist, track, show, …")
                .doesNotContain("episode");
        assertThat(constant(scaffold, "type_top")).isEqualTo("artists");
    }

    @Test
    void scaffold_saysWhoseASplitConstantIs() {
        // when
        var scaffold = withPaths(SPOTIFY_LIKE);

        // then
        assertThat(lineWith(scaffold, "type_top:")).contains("the \"type\" of \"top items\", not search'");
        assertThat(lineWith(scaffold, "  type:")).doesNotContain(" of \"");
    }

    @Test
    void scaffold_makesASegmentIntoASuffixAndNumbersWhatIsLeftOrTaken() {
        // when - "accounts.json" becomes accounts_json; "/{id}" has no fixed segment; a second "top" is taken
        var scaffold = withPaths("""
                /accounts.json/{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: a, schema: { type: string } }
                /{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: "1", schema: { type: integer } }
                /reports/{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: "true", schema: { type: boolean } }
                /more/reports/{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: "1.5", schema: { type: number } }
                """);

        // then
        assertThat(suite(scaffold).get("constants").propertyNames())
                .containsExactly("id", "id_2", "id_reports", "id_3");
        assertThat(suiteText(scaffold)).contains("/accounts.json/${constants.id}");
    }

    @Test
    void scaffold_suffixesFromTheSegmentOfTheConstantsFirstOperation() {
        // when
        var scaffold = withPaths("""
                /v1/accounts/{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: a, schema: { type: string } }
                /v1/charges/{id}:
                  get:
                    parameters:
                      - { name: id, in: path, example: "1", schema: { type: integer } }
                """);

        // then
        assertThat(suite(scaffold).get("constants").propertyNames()).containsExactly("id", "id_charges");
    }

    @Test
    void scaffold_sendsASplitHeaderConstantByItsName() {
        // when
        var scaffold = withPaths("""
                /a:
                  get:
                    parameters:
                      - { name: X-Mode, in: header, required: true, example: fast, schema: { type: string } }
                /b:
                  get:
                    parameters:
                      - { name: X-Mode, in: header, required: true, example: "1", schema: { type: integer } }
                """);

        // then
        var requests = suite(scaffold).get("requests");
        assertThat(requests.get(0)
                        .get("requestDefinition")
                        .get("headers")
                        .get("X-Mode")
                        .asString())
                .isEqualTo("${constants.X-Mode}");
        assertThat(requests.get(1)
                        .get("requestDefinition")
                        .get("headers")
                        .get("X-Mode")
                        .asString())
                .isEqualTo("${constants.X-Mode_b}");
    }

    @Test
    void scaffold_commentsOutOnlyTheConstantsWithoutAValue() {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}/items/{itemId}:
                  delete:
                    parameters:
                      - { name: orderId, in: path, example: "4711" }
                      - { name: itemId, in: path }
                """);

        // then - the key stays, one entry is commented out
        assertThat(suite(scaffold).get("constants").propertyNames()).containsExactly("orderId");
        assertThat(lineWith(scaffold, "itemId:").strip()).startsWith("# itemId:");
        assertThat(lineWith(scaffold, "constants:").strip()).isEqualTo("constants:");
    }

    // --- bodies built from the schema ---

    /** A scaffold of one PUT whose JSON body has {@code schema}, every body kept inline. */
    private static Scaffold withBodySchema(String schema) {
        var paths = """
                /orders:
                  put:
                    summary: update an order
                    requestBody:
                      content:
                        application/json:
                          schema:
                """ + schema.indent(12);
        return OpenApiImporter.scaffold(HEAD + "paths:\n" + paths.indent(2), SOURCE, new ImportOptions(100));
    }

    @Test
    void scaffold_buildsABodyFromThePropertiesExamplesAndBlanksTheRequiredOnes() {
        // when - the walkthrough's Order
        var scaffold = withBodySchema("""
                type: object
                required: [item, quantity, customerRef]
                properties:
                  item: { type: string, example: book }
                  quantity: { type: integer, example: 2 }
                  currency: { type: string, example: EUR }
                  customerRef: { type: string }
                  note: { type: string }
                """);

        // then
        assertThat(JSON.readTree(raw(onlyRequest(scaffold))))
                .isEqualTo(
                        JSON.readTree("{\"item\":\"book\",\"quantity\":2,\"currency\":\"EUR\",\"customerRef\":\"\"}"));
        assertThat(lineWith(scaffold, "body:")).contains("# built from the schema's examples; fill in: customerRef");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("update an order").contains("customerRef"));
    }

    @Test
    void scaffold_writesEachTypesBlankForARequiredPropertyWithoutAValue() {
        // when
        var scaffold = withBodySchema("""
                required: [s, i, n, b, a, o, u]
                properties:
                  s: { type: string }
                  i: { type: integer }
                  n: { type: number }
                  b: { type: boolean }
                  a: { type: array, items: { type: string } }
                  o:
                    type: object
                    required: [inner]
                    properties: { inner: { type: string }, extra: { type: string } }
                  u: {}
                """);

        // then - an object is built of its own required properties, named by path
        assertThat(JSON.readTree(raw(onlyRequest(scaffold))))
                .isEqualTo(JSON.readTree(
                        "{\"s\":\"\",\"i\":0,\"n\":0,\"b\":false,\"a\":[],\"o\":{\"inner\":\"\"},\"u\":\"\"}"));
        assertThat(lineWith(scaffold, "body:")).contains("fill in: s, i, n, b, a, o.inner, u");
    }

    @Test
    void scaffold_usesAnEnumValueAndBuildsNestedObjectsAndArraysFromExamples() {
        // when
        var scaffold = withBodySchema("""
                properties:
                  status: { type: string, enum: [OPEN, SHIPPED] }
                  customer:
                    properties:
                      name: { type: string, example: Ada }
                      nickname: { type: string }
                  lines:
                    type: array
                    items: { properties: { sku: { type: string, example: B-1 } } }
                  tags:
                    type: array
                    items: { type: string }
                """);

        // then - optional parts without a value are left out, and nothing needs filling in
        assertThat(JSON.readTree(raw(onlyRequest(scaffold))))
                .isEqualTo(JSON.readTree(
                        "{\"status\":\"OPEN\",\"customer\":{\"name\":\"Ada\"},\"lines\":[{\"sku\":\"B-1\"}]}"));
        assertThat(lineWith(scaffold, "body:"))
                .contains("# built from the schema's examples")
                .doesNotContain("fill in");
        assertThat(notesIn(scaffold, scaffold.suiteFile())).noneMatch(note -> note.contains("fill in"));
    }

    @Test
    void scaffold_typesAnEnumValueByTheSchemasType() {
        // when
        var scaffold = withBodySchema("""
                properties:
                  level: { type: integer, enum: [3, 5] }
                  strict: { type: boolean, enum: [true] }
                """);

        // then
        assertThat(JSON.readTree(raw(onlyRequest(scaffold)))).isEqualTo(JSON.readTree("{\"level\":3,\"strict\":true}"));
    }

    @Test
    void scaffold_prefersTheMediaTypesExampleToABuiltBody() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: { item: pen }
                          schema: { properties: { item: { type: string, example: book } } }
                """);

        // then
        assertThat(JSON.readTree(raw(onlyRequest(scaffold)))).isEqualTo(JSON.readTree("{\"item\":\"pen\"}"));
        assertThat(lineWith(scaffold, "body:")).doesNotContain("#");
    }

    @Test
    void scaffold_leavesTheBodyEmptyWhenTheSchemaYieldsNothing() {
        // when - only optional properties, none with a value
        var scaffold = withBodySchema("properties:\n  note: { type: string }\n");

        // then
        assertThat(raw(onlyRequest(scaffold))).isEmpty();
        assertThat(lineWith(scaffold, "raw:")).contains("no example in the spec");
    }

    @Test
    void scaffold_writesALongBuiltBodyToAFileKeepingItsComment() {
        // when
        var scaffold = OpenApiImporter.scaffold(HEAD + "paths:\n" + """
                        /orders:
                          put:
                            summary: update an order
                            requestBody:
                              content:
                                application/json:
                                  schema:
                                    required: [a, b]
                                    properties: { a: { type: string }, b: { type: string } }
                        """.indent(2), SOURCE, new ImportOptions(1));

        // then
        assertThat(scaffold.files()).containsKey("bodies/update-an-order.json");
        assertThat(lineWith(scaffold, "body:")).contains("fill in: a, b");
    }

    // --- review additions ---

    @Test
    void scaffold_namesWhoseASplitIsWhenTheBareNameBelongsToAnotherParameter() {
        // when - "type" splits into type_top; then a parameter literally named type_top arrives
        var scaffold = withPaths("""
                /search:
                  get:
                    summary: search
                    parameters:
                      - { name: type, in: query, required: true, example: album, schema: { type: string } }
                /me/top/{type}:
                  get:
                    summary: top items
                    parameters:
                      - { name: type, in: path, example: "1", schema: { type: integer } }
                /stats/{type_top}:
                  get:
                    summary: stats
                    parameters:
                      - { name: type_top, in: path, example: x }
                """);

        // then - the third is split too, and says whose name it could not have
        assertThat(suite(scaffold).get("constants").propertyNames())
                .containsExactly("type", "type_top", "type_top_stats");
        assertThat(lineWith(scaffold, "type_top_stats:")).contains("the \"type_top\" of \"stats\", not top items'");
    }

    @Test
    void scaffold_namesTheOtherExamplesWhenTheValueCameFromALaterRequest() {
        // when - the first has none, the second gives A, the third B
        var scaffold = withPaths("""
                /a/{id}:
                  get:
                    summary: first
                /b/{id}:
                  get:
                    summary: second
                    parameters:
                      - { name: id, in: path, example: A }
                /c/{id}:
                  get:
                    summary: third
                    parameters:
                      - { name: id, in: path, example: B }
                """);

        // then
        assertThat(constant(scaffold, "id")).isEqualTo("A");
        assertThat(suiteText(scaffold)).contains("first usable example; \"third\" uses B");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("third").contains("first usable example"));
    }

    @Test
    void scaffold_leavesTheBodyEmptyWhenASerializedValueHoldsAPlaceholder() {
        // when
        var scaffold = scaffold(HEAD.replace("3.0.3", "3.2.0") + """
                paths:
                  /orders:
                    post:
                      summary: create an order
                      requestBody:
                        content:
                          application/json:
                            examples:
                              one:
                                serializedValue: '{"id":"${vars.id}"}'
                """);

        // then
        assertThat(raw(onlyRequest(scaffold))).isEmpty();
        assertThat(suiteText(scaffold)).doesNotContain("${vars.id}");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("create an order"));
    }

    @Test
    void scaffold_blanksARequiredObjectWithoutRequiredPropertiesAsAnEmptyObject() {
        // when
        var scaffold = withBodySchema("""
                required: [meta, owner]
                properties:
                  meta: { type: object, properties: { x: { type: string } } }
                  owner:
                    type: object
                    required: [id, name]
                    properties: { id: { type: string, example: u-1 }, name: { type: string } }
                """);

        // then - meta is {} and named; owner keeps its required child's example and blanks the other
        assertThat(JSON.readTree(raw(onlyRequest(scaffold))))
                .isEqualTo(JSON.readTree("{\"meta\":{},\"owner\":{\"id\":\"u-1\",\"name\":\"\"}}"));
        assertThat(lineWith(scaffold, "body:")).contains("fill in: meta, owner.name");
    }

    @Test
    void scaffold_leavesAJsonBodyWithNeitherExampleNorSchemaEmpty() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json: {}
                """);

        // then
        assertThat(raw(onlyRequest(scaffold))).isEmpty();
        assertThat(lineWith(scaffold, "raw:")).contains("no example in the spec");
    }

    @Test
    void scaffold_writesAQueryOperationWithItsMethod() {
        // when - 3.2's QUERY: a safe method with a body
        var scaffold = scaffold(HEAD.replace("3.0.3", "3.2.0") + """
                paths:
                  /stations:
                    query:
                      summary: query stations
                      requestBody:
                        content:
                          application/json:
                            example: { near: Berlin }
                      responses:
                        "200": { description: the stations }
                """);

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.get("method").asString()).isEqualTo("QUERY");
        assertThat(JSON.readTree(raw(onlyRequest(scaffold)))).isEqualTo(JSON.readTree("{\"near\":\"Berlin\"}"));
    }

    // --- step 4: which requests carry a body ---

    @ParameterizedTest
    @ValueSource(strings = {"get", "head"})
    void scaffold_sendsNoBodyOnGetOrHeadAndNotesIt(String method) {
        // when - Stripe declares bodies on 305 of these
        var scaffold = withPaths("""
                /accounts:
                  %s:
                    summary: list accounts
                    requestBody:
                      content:
                        application/json:
                          example: { a: 1 }
                """.formatted(method));

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("body")).isFalse();
        assertThat(definition.has("headers")).isFalse();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("list accounts").contains(method.toUpperCase()));
    }

    @Test
    void scaffold_keepsTheBodyOfADeleteThatDeclaresOne() {
        // when
        var scaffold = withPaths("""
                /accounts:
                  delete:
                    summary: close accounts
                    requestBody:
                      content:
                        application/json:
                          example: { ids: [1, 2] }
                """);

        // then
        assertThat(JSON.readTree(raw(onlyRequest(scaffold)))).isEqualTo(JSON.readTree("{\"ids\":[1,2]}"));
    }

    // --- step 4: forms ---

    @Test
    void scaffold_writesOnlyTheRequiredFormFieldsAndNamesTheOptionalOnes() {
        // when
        var scaffold = withPaths("""
                /accounts:
                  post:
                    summary: create an account
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema:
                            required: [country]
                            properties:
                              email: {}
                              country: {}
                              business_type: {}
                """);

        // then
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.propertyNames()).containsExactly("country");
        assertThat(lineWith(scaffold, "form:")).contains("optional: email, business_type");
    }

    @Test
    void scaffold_namesAtMostTenOptionalFormFields() {
        // given
        var properties = new StringBuilder();
        for (var i = 1; i <= 13; i++) {
            properties.append("f").append(i).append(": {}, ");
        }

        // when
        var scaffold = withPaths("""
                /accounts:
                  post:
                    summary: create an account
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema: { properties: { %s } }
                """.formatted(properties));

        // then - no required ones: an empty form, the comment naming the optional
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.isEmpty()).isTrue();
        assertThat(lineWith(scaffold, "form:"))
                .contains("optional: f1, f2, f3, f4, f5, f6, f7, f8, f9, f10 … and 3 more")
                .doesNotContain("f11");
    }

    @Test
    void scaffold_saysHowToSendARequiredNestedFormField() {
        // when
        var scaffold = withPaths("""
                /accounts:
                  post:
                    summary: create an account
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          schema:
                            required: [business_profile, tags]
                            properties:
                              business_profile: { type: object, properties: { name: { type: string } } }
                              tags: { type: array, items: { type: string } }
                """);

        // then
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.get("business_profile").asString()).isEmpty();
        assertThat(lineWith(scaffold, "business_profile:")).contains("business_profile[<key>]");
        assertThat(lineWith(scaffold, "tags:")).contains("tags[<key>]");
    }

    // --- step 4: an operation's own server ---

    private static final String TWO_SERVERS = """
            openapi: 3.0.3
            info:
              title: Orders API
            servers:
              - url: http://localhost:8080
                description: local
              - url: https://api.example.com
                description: prod
            paths:
            """;

    private static Scaffold withServers(String paths) {
        return scaffold(TWO_SERVERS + paths.indent(2));
    }

    @Test
    void scaffold_givesAnOperationsOwnServerAnEnvKeyInEveryEnvironment() {
        // when
        var scaffold = withServers("""
                /assets:
                  servers:
                    - url: https://uploads.example.com/
                  post:
                    summary: upload
                  get:
                    summary: list uploads
                /orders:
                  get:
                    summary: list orders
                """);

        // then
        var requests = allRequests(scaffold);
        assertThat(requests)
                .extracting(r -> r.get("name").asString() + " "
                        + r.get("requestDefinition").get("url").asString())
                .containsExactlyInAnyOrder(
                        "upload ${env.uploadsBaseUrl}/assets",
                        "list uploads ${env.uploadsBaseUrl}/assets",
                        "list orders ${env.baseUrl}/orders");
        for (var environment : List.of("local", "prod")) {
            var env = scaffold.files().get(environment + "/env.yaml");
            assertThat(YAML.readTree(env).get("uploadsBaseUrl").asString())
                    .as(environment)
                    .isEqualTo("https://uploads.example.com");
            assertThat(env.lines()
                            .filter(line -> line.startsWith("uploadsBaseUrl:"))
                            .findFirst()
                            .orElseThrow())
                    .contains("upload, list uploads")
                    .contains("the same in every environment");
            assertThat(env.indexOf("uploadsBaseUrl")).isGreaterThan(env.indexOf("baseUrl:"));
        }
    }

    @Test
    void scaffold_sendsAnOperationServerEqualToADocumentServerToBaseUrl() {
        // when - Twilio's path-level servers repeat the document's
        var scaffold = withServers("""
                /orders:
                  servers:
                    - url: https://api.example.com
                  get:
                    summary: list orders
                """);

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("url").asString())
                .isEqualTo("${env.baseUrl}/orders");
        assertThat(scaffold.files().get("prod/env.yaml")).doesNotContain("BaseUrl");
    }

    @Test
    void scaffold_numbersAServerKeyOnAClashAndFallsBackToServer() {
        // when - two hosts whose first label is "api", and one with no host
        var scaffold = withServers("""
                /a:
                  get:
                    servers: [{ url: "https://api.one.example.com" }]
                /b:
                  get:
                    servers: [{ url: "https://api.two.example.com" }]
                /c:
                  get:
                    servers: [{ url: "https://" }]
                """);

        // then
        var urls = new ArrayList<String>();
        for (var request : suite(scaffold).get("requests")) {
            urls.add(request.get("requestDefinition").get("url").asString());
        }
        assertThat(urls).containsExactly("${env.apiBaseUrl}/a", "${env.apiBaseUrl2}/b", "${env.serverBaseUrl}/c");
    }

    @Test
    void scaffold_prefixesARelativeOperationServerWithBaseUrl() {
        // when
        var scaffold = withServers("""
                /orders:
                  get:
                    summary: list orders
                    servers: [{ url: /v2 }]
                """);

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("url").asString())
                .isEqualTo("${env.baseUrl}/v2/orders");
    }

    @Test
    void scaffold_leavesAServerKeyEmptyAndNotesIt_forAVariableWithoutADefault() {
        // when - DigitalOcean's https://{agent_url}
        var scaffold = withServers("""
                /chat:
                  post:
                    summary: chat
                    servers: [{ url: "https://{agent_url}", variables: { agent_url: { description: yours } } }]
                """);

        // then
        assertThat(YAML.readTree(scaffold.files().get("local/env.yaml"))
                        .get("serverBaseUrl")
                        .asString())
                .isEmpty();
        assertThat(scaffold.notes())
                .anySatisfy(note -> assertThat(note.message()).contains("serverBaseUrl"));
    }

    @Test
    void scaffold_namesAtMostThreeRequestsOfAServerKey() {
        // when
        var scaffold = withServers("""
                /assets:
                  servers: [{ url: "https://uploads.example.com" }]
                  get: { summary: one }
                  post: { summary: two }
                  put: { summary: three }
                  delete: { summary: four }
                  patch: { summary: five }
                """);

        // then
        assertThat(scaffold.files().get("local/env.yaml")).contains("one, two, three and 2 more");
    }

    // --- step 4: grouping by path segment ---

    @Test
    void scaffold_groupsByFirstPathSegmentWhenNoOperationIsTagged() {
        // when - Stripe-shaped: versions skipped, a parameter segment skipped, one-of-a-kind stays on top
        var scaffold = withPaths("""
                /v1/accounts:
                  get: { summary: list accounts }
                /v1/accounts/{id}:
                  get: { summary: read an account }
                /2010-04-01/charges:
                  post: { summary: create a charge }
                /v2beta/charges/{id}:
                  get: { summary: read a charge }
                /{tenant}/accounts/stats:
                  get: { summary: account stats }
                /health:
                  get: { summary: health }
                """);

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("health");
        assertThat(names(suite.get("subSuites"))).containsExactly("accounts", "charges");
        assertThat(names(suite.get("subSuites").get(0).get("requests")))
                .containsExactly("list accounts", "read an account", "account stats");
    }

    @Test
    void scaffold_doesNotGroupByPathWhenAnyOperationIsTagged() {
        // when
        var scaffold = withPaths("""
                /v1/accounts:
                  get: { summary: list accounts, tags: [admin] }
                /v1/charges:
                  get: { summary: list charges }
                /v1/charges/{id}:
                  get: { summary: read a charge }
                """);

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("list charges", "read a charge");
        assertThat(names(suite.get("subSuites"))).containsExactly("admin");
    }

    // --- step 4: where auth is written ---

    private static final String KEYS = """
            openapi: 3.0.3
            info:
              title: Orders API
            servers:
              - url: http://localhost:8080
                description: local
            components:
              securitySchemes:
                bulkKey: { type: apiKey, in: header, name: X-Bulk-Key }
                token: { type: http, scheme: bearer }
            paths:
            """;

    @Test
    void scaffold_writesASubSuitesSharedAuthOnceOnTheSubSuite() {
        // when
        var scaffold = scaffold(KEYS + """
                /import:
                  post: { tags: [bulk], summary: import, security: [{ bulkKey: [] }] }
                  get: { tags: [bulk], summary: status, security: [{ bulkKey: [] }] }
                /orders:
                  get: { tags: [orders], summary: list, security: [{ bulkKey: [] }] }
                  post: { tags: [orders], summary: create, security: [{ token: [] }] }
                """.indent(2));

        // then - bulk shares one; orders differs, so each keeps its own
        var subSuites = suite(scaffold).get("subSuites");
        assertThat(subSuites.get(0).get("auth").get("type").asString()).isEqualTo("apikey");
        assertThat(subSuites.get(0).get("requests").get(0).has("auth")).isFalse();
        assertThat(lineWith(scaffold, "auth:")).contains("the spec's security for every request here: bulkKey");
        assertThat(subSuites.get(1).has("auth")).isFalse();
        assertThat(subSuites.get(1).get("requests").get(0).has("auth")).isTrue();
    }

    @Test
    void scaffold_keepsTheAuthOnTheRequestOfASubSuiteOfOne() {
        // when
        var scaffold = scaffold(KEYS + """
                /import:
                  post: { tags: [bulk], summary: import, security: [{ bulkKey: [] }] }
                """.indent(2));

        // then
        var subSuite = suite(scaffold).get("subSuites").get(0);
        assertThat(subSuite.has("auth")).isFalse();
        assertThat(subSuite.get("requests").get(0).get("auth").get("type").asString())
                .isEqualTo("apikey");
    }

    @Test
    void scaffold_writesAuthOnceOnTheSuiteWhenEveryRequestCarriesTheSameWithoutADefault() {
        // when - Petstore-shaped: no document security, every operation the same scheme
        var scaffold = scaffold(KEYS + """
                /a:
                  get: { summary: a, security: [{ token: [] }] }
                /b:
                  get: { tags: [b], summary: b, security: [{ token: [] }] }
                /b/{id}:
                  get: { tags: [b], summary: one b, security: [{ token: [] }] }
                """.indent(2));

        // then
        var suite = suite(scaffold);
        assertThat(suite.get("auth").get("type").asString()).isEqualTo("bearer");
        assertThat(suite.get("requests").get(0).has("auth")).isFalse();
        assertThat(suite.get("subSuites").get(0).has("auth")).isFalse();
        assertThat(lineWith(scaffold, "auth:")).contains("the spec's security for every request: token");
    }

    // --- step 4: environment names ---

    @Test
    void scaffold_namesAnEnvironmentByTheDescriptionsFirstFourWords() {
        // when
        var scaffold = scaffold("""
                openapi: 3.0.3
                info:
                  title: Trains
                servers:
                  - url: https://mock.example.com
                    description: A mock server for testing and development purposes
                  - url: https://staging.example.com
                    description: The staging server, reset nightly
                  - url: https://prod.example.com
                    description: an extraordinarily-long-hyphenated-name for production
                paths: {}
                """);

        // then - an article dropped; at most 30 characters, cut where a word ends
        assertThat(scaffold.environments())
                .containsExactly("mock-server-for-testing", "staging-server-reset-nightly", "extraordinarily-long");
    }

    @Test
    void scaffold_dropsTrailingFillerWordsFromAnEnvironmentName() {
        // when - Train Travel's, and a single filler word, which is kept rather than leaving nothing
        var scaffold = scaffold("""
                openapi: 3.0.3
                info:
                  title: Trains
                servers:
                  - url: https://api.example.com
                    description: The production server for the Train Travel API
                  - url: https://other.example.com
                    description: for
                paths: {}
                """);

        // then
        assertThat(scaffold.environments()).containsExactly("production-server", "for");
    }

    @Test
    void scaffold_cutsAnEnvironmentNameWithoutAWordEndAtThirtyCharacters() {
        // when
        var scaffold = scaffold("""
                openapi: 3.0.3
                info:
                  title: Trains
                servers:
                  - url: https://api.example.com
                    description: Supercalifragilisticexpialidocious-and-more server
                paths: {}
                """);

        // then
        assertThat(scaffold.environments()).containsExactly("supercalifragilisticexpialidoc");
    }

    // --- step 4: what is not imported, and comments ---

    @Test
    void scaffold_notesWhatTheSpecHoldsThatIsNotImported() {
        // when
        var scaffold = scaffold(HEAD.replace("3.0.3", "3.2.0") + """
                webhooks:
                  orderShipped: {}
                paths:
                  /orders:
                    additionalOperations:
                      COPY: {}
                    get: { summary: list }
                """);

        // then
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .contains(
                        "not imported: webhooks: orderShipped", "not imported: additionalOperations on /orders: COPY");
        assertThat(suiteText(scaffold)).doesNotContain("only webhooks");
    }

    @Test
    void scaffold_saysSoWhenTheSpecDefinesOnlyWebhooks() {
        // when
        var scaffold = scaffold(HEAD.replace("3.0.3", "3.1.0") + "webhooks:\n  orderShipped: {}\n");

        // then
        assertThat(suiteText(scaffold).lines().toList().get(1))
                .isEqualTo("# The spec defines only webhooks, which are not imported.");
    }

    @Test
    void scaffold_saysWhatToWriteForAnyOtherMediaType() {
        // when
        var scaffold = withPaths("""
                /documents:
                  post:
                    summary: upload
                    requestBody:
                      content:
                        application/pdf: {}
                """);

        // then
        assertThat(lineWith(scaffold, "raw:")).contains("write the application/pdf body here");
    }

    @Test
    void scaffold_putsWhatToDoAboutAnAuthOnItsTypeLine() {
        // when - an authorization-code flow: a token the author has to obtain
        var scaffold = scaffold("""
                openapi: 3.0.3
                info:
                  title: Orders API
                servers:
                  - url: http://localhost:8080
                security:
                  - user: []
                components:
                  securitySchemes:
                    user:
                      type: oauth2
                      flows:
                        authorizationCode:
                          authorizationUrl: https://login.example.com/authorize
                          tokenUrl: https://login.example.com/token
                          scopes: {}
                paths:
                  /orders:
                    get: { summary: list }
                """);

        // then - no comment line of its own between type: and args:
        assertThat(lineWith(scaffold, "type: bearer")).contains("obtain a token");
        var lines = suiteText(scaffold).lines().toList();
        var type = lines.indexOf(lineWith(scaffold, "type: bearer"));
        assertThat(lines.get(type + 1).strip()).isEqualTo("args:");
    }

    // --- review additions, step 4 ---

    @Test
    void scaffold_writesAFormFromItsExampleWhenTheSchemaNamesNoFields() {
        // when
        var scaffold = withPaths("""
                /token:
                  post:
                    summary: get a token
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          example: { grant_type: client_credentials, scope: [a, b], audience: "${vars.aud}" }
                """);

        // then - each key an entry; a list is nested, a placeholder left to fill in
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.propertyNames()).containsExactly("grant_type", "scope", "audience");
        assertThat(form.get("grant_type").asString()).isEqualTo("client_credentials");
        assertThat(lineWith(scaffold, "scope:")).contains("scope[<key>]");
        assertThat(form.get("audience").asString()).isEmpty();
        assertThat(lineWith(scaffold, "form:")).contains("the spec's example");
    }

    @Test
    void scaffold_writesAnEmptyFormWhenTheSchemaNamesNoFieldsAndTheExampleIsNoMapping() {
        // when
        var scaffold = withPaths("""
                /token:
                  post:
                    summary: get a token
                    requestBody:
                      content:
                        application/x-www-form-urlencoded:
                          example: grant_type=client_credentials
                """);

        // then
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.isEmpty()).isTrue();
        assertThat(lineWith(scaffold, "form:")).contains("the spec names no fields");
    }

    @Test
    void scaffold_doesNotHoistASharedNoneOrAQueryKey() {
        // when - both requests carry none of their own, then both an api key in the query
        var scaffold = scaffold("""
                openapi: 3.0.3
                info:
                  title: Orders API
                servers:
                  - url: http://localhost:8080
                security:
                  - token: []
                components:
                  securitySchemes:
                    token: { type: http, scheme: bearer }
                    queryKey: { type: apiKey, in: query, name: key }
                paths:
                  /public:
                    get: { tags: [public], summary: one, security: [] }
                    post: { tags: [public], summary: two, security: [] }
                  /keyed:
                    get: { tags: [keyed], summary: three, security: [{ queryKey: [] }] }
                    post: { tags: [keyed], summary: four, security: [{ queryKey: [] }] }
                """);

        // then - none is not a block worth hoisting, and a query key has no block at all
        var subSuites = suite(scaffold).get("subSuites");
        assertThat(subSuites.get(0).has("auth")).isFalse();
        assertThat(subSuites
                        .get(0)
                        .get("requests")
                        .get(0)
                        .get("auth")
                        .get("type")
                        .asString())
                .isEqualTo("none");
        assertThat(subSuites.get(1).has("auth")).isFalse();
        assertThat(subSuites
                        .get(1)
                        .get("requests")
                        .get(0)
                        .get("requestDefinition")
                        .get("url")
                        .asString())
                .contains("key=${secrets.apiKey}");
    }

    @Test
    void scaffold_joinsARelativeOperationServerWithoutASlashOrWithNothing() {
        // when
        var scaffold = withServers("""
                /orders:
                  get:
                    summary: v2 orders
                    servers: [{ url: v2 }]
                /stock:
                  get:
                    summary: stock
                    servers: [{ url: "/" }]
                """);

        // then
        var urls = new ArrayList<String>();
        for (var request : allRequests(scaffold)) {
            urls.add(request.get("requestDefinition").get("url").asString());
        }
        assertThat(urls).containsExactly("${env.baseUrl}/v2/orders", "${env.baseUrl}/stock");
    }
}

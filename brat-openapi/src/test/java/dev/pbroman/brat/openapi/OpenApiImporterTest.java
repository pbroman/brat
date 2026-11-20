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
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
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

        // then
        assertThat(names(suite(scaffold).get("requests")))
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
        var requests = suite(scaffold).get("requests");
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
    void scaffold_leavesAConstantEmptyAndNotesIt_whenThereIsNoExample() {
        // when
        var scaffold = withPaths("""
                /orders/{orderId}:
                  get:
                    summary: read an order
                """);

        // then
        assertThat(onlyRequest(scaffold).get("requestDefinition").get("url").asString())
                .isEqualTo("${env.baseUrl}/orders/${constants.orderId}");
        assertThat(constant(scaffold, "orderId")).isEmpty();
        assertThat(lineWith(scaffold, "orderId:")).contains("#");
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("orderId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"${vars.id}\"", "[1, 2]", "{ a: 1 }"})
    void scaffold_leavesAConstantEmptyAndNotesIt_whenTheExampleIsNotASafeSingleValue(String example) {
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
        assertThat(constant(scaffold, "orderId")).isEmpty();
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

    @Test
    void scaffold_writesTextThatIsNotJsonAsItIsWithAComment() {
        // when
        var scaffold = withPaths("""
                /orders:
                  post:
                    summary: create an order
                    requestBody:
                      content:
                        application/json:
                          example: 'item=book'
                """);

        // then
        assertThat(raw(onlyRequest(scaffold)).strip()).isEqualTo("item=book");
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
                          schema: { properties: { token: {} } }
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
                          schema: { properties: { user: {} } }
                """.formatted(type));

        // then
        var definition = onlyRequest(scaffold).get("requestDefinition");
        assertThat(definition.has("headers")).isFalse();
        assertThat(definition.get("body").get("form").propertyNames()).containsExactly("user");
    }

    @Test
    void scaffold_writesAnEmptyFormAndNotesIt_whenTheSchemaHasNoProperties() {
        // when
        var scaffold = withPaths("""
                /login:
                  post:
                    summary: log in
                    requestBody:
                      content:
                        application/x-www-form-urlencoded: {}
                """);

        // then
        var form = onlyRequest(scaffold).get("requestDefinition").get("body").get("form");
        assertThat(form.isObject()).isTrue();
        assertThat(form.isEmpty()).isTrue();
        assertThat(notesIn(scaffold, scaffold.suiteFile()))
                .anySatisfy(note -> assertThat(note).contains("log in"));
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
                            schema: { properties: { a: {}, b: {}, c: {} } }
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
}

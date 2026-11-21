package dev.pbroman.brat.openapi;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.secrets.FlatYamlLoader;
import dev.pbroman.brat.openapi.Scaffold.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The mapping from a spec's {@code security} to auth, through {@link OpenApiImporter#scaffold}. */
class OpenApiImporterAuthTest {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private static final String HEAD = """
            openapi: 3.0.3
            info:
              title: Orders API
            servers:
              - url: http://localhost:8080
                description: local
              - url: https://staging.example.com
                description: staging
            """;

    private static final String SCHEMES = """
            components:
              securitySchemes:
                bearer:
                  type: http
                  scheme: bearer
                admin:
                  type: http
                  scheme: Bearer
                basic:
                  type: http
                  scheme: basic
                digest:
                  type: http
                  scheme: digest
                headerKey:
                  type: apiKey
                  in: header
                  name: X-API-Key
                queryKey:
                  type: apiKey
                  in: query
                  name: api key
                cookieKey:
                  type: apiKey
                  in: cookie
                  name: session
                namelessKey:
                  type: apiKey
                  in: header
                mtls:
                  type: mutualTLS
                oidc:
                  type: openIdConnect
                  openIdConnectUrl: https://login.example.com/.well-known/openid-configuration
                code:
                  type: oauth2
                  flows:
                    authorizationCode:
                      authorizationUrl: https://login.example.com/authorize
                      tokenUrl: https://login.example.com/token
                      scopes: {}
                clientCredentials:
                  type: oauth2
                  flows:
                    clientCredentials:
                      tokenUrl: https://login.example.com/oauth/token
                      scopes: {}
                partner:
                  type: oauth2
                  flows:
                    clientCredentials:
                      tokenUrl: https://partner.example.com/token
                      scopes: {}
            """;

    /** A scaffold of a spec with every scheme, the document's {@code security} and {@code paths}. */
    private static Scaffold scaffold(String security, String paths) {
        var spec = HEAD + SCHEMES + (security == null ? "" : "security:\n" + security.indent(2)) + "paths:\n"
                + paths.indent(2);
        return OpenApiImporter.scaffold(spec, "orders-openapi.yaml", ImportOptions.defaults());
    }

    private static final String ONE_OPERATION = """
            /orders:
              get:
                summary: list orders
                responses:
                  "200": { description: ok }
            """;

    private static String suiteText(Scaffold scaffold) {
        return scaffold.files().get(scaffold.suiteFile());
    }

    private static JsonNode suite(Scaffold scaffold) {
        return YAML.readTree(suiteText(scaffold));
    }

    /** The request of that name, at the top of the suite or in a sub-suite. */
    private static JsonNode request(Scaffold scaffold, String name) {
        var suite = suite(scaffold);
        var lists = new ArrayList<JsonNode>();
        if (suite.has("requests")) {
            lists.add(suite.get("requests"));
        }
        if (suite.has("subSuites")) {
            for (var subSuite : suite.get("subSuites")) {
                lists.add(subSuite.get("requests"));
            }
        }
        for (var requests : lists) {
            for (var request : requests) {
                if (request.get("name").asString().equals(name)) {
                    return request;
                }
            }
        }
        throw new AssertionError("no request " + name);
    }

    private static List<String> names(JsonNode nodes) {
        var names = new ArrayList<String>();
        for (var node : nodes) {
            names.add(node.get("name").asString());
        }
        return names;
    }

    private static String lineWith(Scaffold scaffold, String fragment) {
        return suiteText(scaffold)
                .lines()
                .filter(line -> line.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line holds " + fragment));
    }

    private static List<String> secretsNoted(Scaffold scaffold) {
        return scaffold.notes().stream()
                .map(Note::secret)
                .filter(secret -> secret != null)
                .toList();
    }

    private static List<String> messages(Scaffold scaffold) {
        return scaffold.notes().stream().map(Note::message).toList();
    }

    // --- the suite's auth, per scheme kind ---

    @Test
    void scaffold_mapsHttpBearerToBearerWithASecretToken() {
        // when
        var scaffold = scaffold("- bearer: []", ONE_OPERATION);

        // then
        var auth = suite(scaffold).get("auth");
        assertThat(auth.get("type").asString()).isEqualTo("bearer");
        assertThat(auth.get("args").get("token").asString()).isEqualTo("${secrets.token}");
        assertThat(lineWith(scaffold, "auth:")).contains("#").contains("bearer");
        assertThat(request(scaffold, "list orders").has("auth")).isFalse();
    }

    @Test
    void scaffold_namesASecretsEnvironmentVariableAndNotesItOnce() {
        // when
        var scaffold = scaffold("- bearer: []", ONE_OPERATION);

        // then
        assertThat(lineWith(scaffold, "${secrets.token}")).contains("BRAT_SECRET_TOKEN");
        assertThat(secretsNoted(scaffold)).containsExactly("token");
        var note = scaffold.notes().stream()
                .filter(n -> "token".equals(n.secret()))
                .findFirst()
                .orElseThrow();
        assertThat(note.file()).isEqualTo(scaffold.suiteFile());
        assertThat(note.message()).contains("BRAT_SECRET_TOKEN");
    }

    @Test
    void scaffold_mapsHttpBasicToBasicWithSecretUsernameAndPassword() {
        // when
        var scaffold = scaffold("- basic: []", ONE_OPERATION);

        // then
        var auth = suite(scaffold).get("auth");
        assertThat(auth.get("type").asString()).isEqualTo("basic");
        assertThat(auth.get("args").get("username").asString()).isEqualTo("${secrets.username}");
        assertThat(auth.get("args").get("password").asString()).isEqualTo("${secrets.password}");
        assertThat(secretsNoted(scaffold)).containsExactly("username", "password");
    }

    @Test
    void scaffold_mapsAHeaderApiKeyToApikeyWithItsHeader() {
        // when
        var scaffold = scaffold("- headerKey: []", ONE_OPERATION);

        // then
        var auth = suite(scaffold).get("auth");
        assertThat(auth.get("type").asString()).isEqualTo("apikey");
        assertThat(auth.get("args").get("key").asString()).isEqualTo("${secrets.apiKey}");
        assertThat(auth.get("args").get("header").asString()).isEqualTo("X-API-Key");
    }

    @Test
    void scaffold_sendsAQueryApiKeyInEveryRequestsUrl() {
        // when
        var scaffold = scaffold("- queryKey: []", """
                /orders:
                  get:
                    summary: list orders
                    parameters:
                      - { name: status, in: query, required: true, example: OPEN }
                    responses:
                      "200": { description: ok }
                /health:
                  get:
                    summary: health check
                    responses:
                      "200": { description: ok }
                """);

        // then
        assertThat(suite(scaffold).has("auth")).isFalse();
        assertThat(request(scaffold, "list orders")
                        .get("requestDefinition")
                        .get("url")
                        .asString())
                .isEqualTo("${env.baseUrl}/orders?status=${constants.status}&api%20key=${secrets.apiKey}");
        assertThat(request(scaffold, "health check")
                        .get("requestDefinition")
                        .get("url")
                        .asString())
                .isEqualTo("${env.baseUrl}/health?api%20key=${secrets.apiKey}");
        assertThat(secretsNoted(scaffold)).containsExactly("apiKey");
        assertThat(suiteText(scaffold)).contains("BRAT_SECRET_API_KEY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "oidc"})
    void scaffold_mapsAnotherOauth2FlowOrOpenIdConnectToBearerWithASecretTokenAndANote(String scheme) {
        // when
        var scaffold = scaffold("- " + scheme + ": []", ONE_OPERATION);

        // then
        var auth = suite(scaffold).get("auth");
        assertThat(auth.get("type").asString()).isEqualTo("bearer");
        assertThat(auth.get("args").get("token").asString()).isEqualTo("${secrets.token}");
        assertThat(scaffold.setupRequests()).isZero();
        assertThat(messages(scaffold)).anySatisfy(message -> assertThat(message).contains(scheme));
    }

    @ParameterizedTest
    @ValueSource(strings = {"digest", "cookieKey", "namelessKey", "mtls", "undefined"})
    void scaffold_mapsASchemeItCannotImportToNoneWithANote(String scheme) {
        // when
        var scaffold = scaffold("- " + scheme + ": []", ONE_OPERATION);

        // then
        assertThat(suite(scaffold).get("auth").get("type").asString()).isEqualTo("none");
        assertThat(messages(scaffold)).anySatisfy(message -> assertThat(message).contains(scheme));
        assertThat(secretsNoted(scaffold)).isEmpty();
    }

    // --- client credentials ---

    @Test
    void scaffold_fetchesAClientCredentialsTokenInASetupRequest() {
        // when
        var scaffold = scaffold("- clientCredentials: [orders.read, orders.write]", """
                /orders:
                  get:
                    summary: list orders
                    security:
                      - clientCredentials: [orders.read, orders.admin]
                    responses:
                      "200": { description: ok }
                """);

        // then
        var suite = suite(scaffold);
        assertThat(names(suite.get("requests"))).containsExactly("get a token", "list orders");
        assertThat(scaffold.setupRequests()).isEqualTo(1);
        assertThat(scaffold.requests()).isEqualTo(1);
        var auth = suite.get("auth");
        assertThat(auth.get("type").asString()).isEqualTo("bearer");
        assertThat(auth.get("args").get("token").asString()).isEqualTo("${vars.accessToken}");
        assertThat(lineWith(scaffold, "${vars.accessToken}")).contains("get a token");

        var token = request(scaffold, "get a token");
        assertThat(token.get("phase").asString()).isEqualTo("setup");
        assertThat(token.get("skipCondition").get("a").asString()).isEqualTo("${vars.accessToken}");
        assertThat(token.get("skipCondition").get("func").asString()).isEqualTo("isNotBlank");
        assertThat(token.get("skipCondition").has("b")).isFalse();
        assertThat(lineWith(scaffold, "skipCondition:")).contains("#").contains("vars.accessToken");
        assertThat(token.get("auth").get("type").asString()).isEqualTo("none");
        var definition = token.get("requestDefinition");
        assertThat(definition.get("url").asString()).isEqualTo("${env.tokenUrl}");
        assertThat(definition.get("method").asString()).isEqualTo("POST");
        var form = definition.get("body").get("form");
        assertThat(form.propertyNames()).containsExactly("grant_type", "client_id", "client_secret", "scope");
        assertThat(form.get("grant_type").asString()).isEqualTo("client_credentials");
        assertThat(form.get("client_id").asString()).isEqualTo("${env.clientId}");
        assertThat(form.get("client_secret").asString()).isEqualTo("${secrets.clientSecret}");
        assertThat(form.get("scope").asString()).isEqualTo("orders.read orders.write orders.admin");
        var assertion = token.get("responseActions").get("assertions").get(0);
        assertThat(assertion.get("a").asString()).isEqualTo("${response.statusCode}");
        assertThat(assertion.get("b").asInt()).isEqualTo(200);
        assertThat(token.get("responseActions")
                        .get("setVars")
                        .get("accessToken")
                        .asString())
                .isEqualTo("${response.json.$.access_token}");
        assertThat(secretsNoted(scaffold)).containsExactly("clientSecret");
        assertThat(lineWith(scaffold, "client_secret:")).contains("BRAT_SECRET_CLIENT_SECRET");
    }

    @Test
    void scaffold_givesEveryEnvironmentTheTokenUrlAndAnEmptyClientId() {
        // when
        var scaffold = scaffold("- clientCredentials: []", ONE_OPERATION);

        // then
        for (var environment : List.of("local", "staging")) {
            var file = environment + "/env.yaml";
            assertThat(FlatYamlLoader.load(scaffold.files().get(file)))
                    .containsEntry("tokenUrl", "https://login.example.com/oauth/token")
                    .containsEntry("clientId", "");
            assertThat(scaffold.notes())
                    .anySatisfy(note -> assertThat(note.file()).isEqualTo(file))
                    .filteredOn(note -> note.file().equals(file))
                    .anySatisfy(note -> assertThat(note.message()).contains("clientId"));
        }
    }

    @Test
    void scaffold_requestsNoScopeWhenTheSpecNamesNone() {
        // when
        var scaffold = scaffold("- clientCredentials: []", ONE_OPERATION);

        // then
        var form = request(scaffold, "get a token")
                .get("requestDefinition")
                .get("body")
                .get("form");
        assertThat(form.has("scope")).isFalse();
    }

    @Test
    void scaffold_letsTheTokenRequestClaimItsNameBeforeAnOperation() {
        // when
        var scaffold = scaffold("- clientCredentials: []", """
                /token:
                  get:
                    summary: get a token
                    responses:
                      "200": { description: ok }
                """);

        // then
        assertThat(names(suite(scaffold).get("requests"))).containsExactly("get a token", "get a token 2");
    }

    @Test
    void scaffold_addsNoTokenRequestForASchemeNoOperationUses() {
        // when
        var scaffold = scaffold("- bearer: []", ONE_OPERATION);

        // then
        assertThat(scaffold.setupRequests()).isZero();
        assertThat(FlatYamlLoader.load(scaffold.files().get("local/env.yaml"))).doesNotContainKey("tokenUrl");
    }

    // --- requests overriding the suite ---

    @Test
    void scaffold_givesARequestItsOwnAuthWhereItsSecurityDiffers() {
        // when
        var scaffold = scaffold("- bearer: []", """
                /orders:
                  get:
                    summary: list orders
                    responses:
                      "200": { description: ok }
                  post:
                    summary: create an order
                    security:
                      - bearer: []
                    responses:
                      "201": { description: created }
                  delete:
                    summary: delete orders
                    security:
                      - basic: []
                    responses:
                      "204": { description: deleted }
                """);

        // then
        assertThat(request(scaffold, "list orders").has("auth")).isFalse();
        assertThat(request(scaffold, "create an order").has("auth")).isFalse();
        var own = request(scaffold, "delete orders").get("auth");
        assertThat(own.get("type").asString()).isEqualTo("basic");
        assertThat(lineWith(scaffold, "type: basic")).doesNotContain("#");
        assertThat(suiteText(scaffold)
                        .lines()
                        .filter(line -> line.contains("auth:"))
                        .toList())
                .hasSize(2)
                .last()
                .satisfies(line -> assertThat(line).contains("#").contains("basic"));
    }

    @Test
    void scaffold_cancelsTheSuitesAuthForAnOperationWithEmptySecurity() {
        // when
        var scaffold = scaffold("- bearer: []", """
                /health:
                  get:
                    summary: health check
                    security: []
                    responses:
                      "200": { description: up }
                """);

        // then
        var own = request(scaffold, "health check").get("auth");
        assertThat(own.get("type").asString()).isEqualTo("none");
        assertThat(own.has("args")).isFalse();
        assertThat(suiteText(scaffold)).contains("security: []");
    }

    @Test
    void scaffold_writesNoAuthForEmptySecurityWhereTheSuiteHasNone() {
        // when
        var scaffold = scaffold(null, """
                /health:
                  get:
                    summary: health check
                    security: []
                    responses:
                      "200": { description: up }
                """);

        // then
        assertThat(suite(scaffold).has("auth")).isFalse();
        assertThat(request(scaffold, "health check").has("auth")).isFalse();
    }

    @Test
    void scaffold_givesEachRequestItsAuthWhereOnlyOperationsDeclareSecurity() {
        // when
        var scaffold = scaffold(null, """
                /orders:
                  get:
                    summary: list orders
                    security:
                      - bearer: []
                    responses:
                      "200": { description: ok }
                """);

        // then
        assertThat(suite(scaffold).has("auth")).isFalse();
        assertThat(request(scaffold, "list orders").get("auth").get("type").asString())
                .isEqualTo("bearer");
    }

    @Test
    void scaffold_sendsNoCredentialsForAnOperationWithoutSecurityWhereOnlyOthersDeclareIt() {
        // when
        var scaffold = scaffold(null, """
                /orders:
                  get:
                    summary: list orders
                    security:
                      - bearer: []
                    responses:
                      "200": { description: ok }
                /health:
                  get:
                    summary: health check
                    responses:
                      "200": { description: up }
                """);

        // then
        assertThat(request(scaffold, "health check").has("auth")).isFalse();
        assertThat(request(scaffold, "health check")
                        .get("requestDefinition")
                        .get("url")
                        .asString())
                .isEqualTo("${env.baseUrl}/health");
    }

    @Test
    void scaffold_notesBothWhatIsNotImportedAndASchemeRequiredAsWell() {
        // when
        var scaffold = scaffold("- digest: []\n  basic: []", ONE_OPERATION);

        // then
        assertThat(suite(scaffold).get("auth").get("type").asString()).isEqualTo("none");
        assertThat(messages(scaffold))
                .anySatisfy(message -> assertThat(message).contains("digest").contains("basic"));
    }

    @Test
    void scaffold_notesASchemeRequiredBesideAQueryApiKeyOnTheUrl() {
        // when
        var scaffold = scaffold("- queryKey: []\n  headerKey: []", ONE_OPERATION);

        // then
        assertThat(suite(scaffold).has("auth")).isFalse();
        assertThat(suiteText(scaffold)).contains("headerKey");
        assertThat(messages(scaffold))
                .anySatisfy(
                        message -> assertThat(message).contains("list orders").contains("headerKey"));
    }

    @Test
    void scaffold_mapsAnEmptyFirstAlternativeToNone() {
        // when
        var scaffold = scaffold("- {}\n- bearer: []", ONE_OPERATION);

        // then
        assertThat(suite(scaffold).get("auth").get("type").asString()).isEqualTo("none");
        assertThat(secretsNoted(scaffold)).isEmpty();
    }

    @Test
    void scaffold_usesTheFirstSchemeAndNotesOneTheSpecRequiresAsWell() {
        // when
        var scaffold = scaffold("- bearer: []\n  headerKey: []\n- basic: []", ONE_OPERATION);

        // then
        assertThat(suite(scaffold).get("auth").get("type").asString()).isEqualTo("bearer");
        assertThat(messages(scaffold)).anySatisfy(message -> assertThat(message).contains("headerKey"));
        assertThat(suiteText(scaffold)).doesNotContain("${secrets.username}");
    }

    // --- keys ---

    @Test
    void scaffold_givesALaterSchemeItsOwnNameInFrontOfASharedKey() {
        // when
        var scaffold = scaffold("- bearer: []", """
                /admin:
                  delete:
                    summary: purge
                    security:
                      - admin: []
                    responses:
                      "204": { description: purged }
                """);

        // then
        assertThat(suite(scaffold).get("auth").get("args").get("token").asString())
                .isEqualTo("${secrets.token}");
        assertThat(request(scaffold, "purge")
                        .get("auth")
                        .get("args")
                        .get("token")
                        .asString())
                .isEqualTo("${secrets.admin.token}");
        assertThat(secretsNoted(scaffold)).containsExactly("token", "admin.token");
        assertThat(suiteText(scaffold)).contains("BRAT_SECRET_ADMIN_TOKEN");
    }

    @Test
    void scaffold_keepsTwoClientCredentialsSchemesApart() {
        // when
        var scaffold = scaffold("- clientCredentials: []", """
                /partner:
                  get:
                    summary: partner orders
                    security:
                      - partner: [partner.read]
                    responses:
                      "200": { description: ok }
                """);

        // then
        assertThat(names(suite(scaffold).get("requests")))
                .containsExactly("get a token", "get a token for partner", "partner orders");
        assertThat(scaffold.setupRequests()).isEqualTo(2);
        var partnerToken = request(scaffold, "get a token for partner");
        assertThat(partnerToken.get("requestDefinition").get("url").asString()).isEqualTo("${env.partner.tokenUrl}");
        var form = partnerToken.get("requestDefinition").get("body").get("form");
        assertThat(form.get("client_id").asString()).isEqualTo("${env.partner.clientId}");
        assertThat(form.get("client_secret").asString()).isEqualTo("${secrets.partner.clientSecret}");
        assertThat(partnerToken.get("skipCondition").get("a").asString()).isEqualTo("${vars.partner.accessToken}");
        assertThat(partnerToken.get("responseActions").get("setVars").has("partner.accessToken"))
                .isTrue();
        assertThat(request(scaffold, "partner orders")
                        .get("auth")
                        .get("args")
                        .get("token")
                        .asString())
                .isEqualTo("${vars.partner.accessToken}");
        assertThat(FlatYamlLoader.load(scaffold.files().get("local/env.yaml")))
                .containsEntry("tokenUrl", "https://login.example.com/oauth/token")
                .containsEntry("partner.tokenUrl", "https://partner.example.com/token")
                .containsEntry("partner.clientId", "");
    }

    // --- notes ---

    @Test
    void scaffold_leavesTheSecretOfEveryOtherNoteNull() {
        // when
        var scaffold = scaffold("- clientCredentials: []", """
                /orders/{orderId}:
                  get:
                    summary: read an order
                """);

        // then
        assertThat(scaffold.notes())
                .filteredOn(note -> note.secret() != null)
                .extracting(Note::secret)
                .containsExactly("clientSecret");
        assertThat(scaffold.notes()).filteredOn(note -> note.secret() == null).isNotEmpty();
    }
}

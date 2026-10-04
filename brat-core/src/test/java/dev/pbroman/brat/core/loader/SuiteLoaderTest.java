package dev.pbroman.brat.core.loader;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.HTTP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuiteLoaderTest {

    private final SuiteLoader underTest = SuiteLoader.httpOnly();

    // ---------- the happy path ----------

    @Test
    void load_bindsTheWholeDocument() {
        // given
        var yaml = """
                name: order api
                constants:
                  contentType: application/json
                requests:
                  - name: create an order
                    id: order-create
                    requestDefinition:
                      url: "${env.baseUrl}/orders"
                      method: POST
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(suite.name()).isEqualTo("order api");
        assertThat(suite.constants()).containsEntry("contentType", "application/json");
        assertThat(suite.requests())
                .singleElement()
                .satisfies(r -> assertThat(r.id()).isEqualTo("order-create"));
    }

    @Test
    void load_acceptsAnEnumInAnyCase() {
        // given - the suite-author doc writes `phase: setup`, the assertions doc writes `severity: WARN`
        var lower = underTest.load("name: s\nrequests:\n  - name: r\n    phase: teardown\n");
        var upper = underTest.load("name: s\nrequests:\n  - name: r\n    phase: TEARDOWN\n");

        // then
        assertThat(lower.requests().getFirst().phase()).isEqualTo(Phase.TEARDOWN);
        assertThat(upper.requests().getFirst().phase()).isEqualTo(Phase.TEARDOWN);
    }

    @Test
    void load_rejectsAPhaseOnASuite() {
        // given - only requests take a phase
        var yaml = "name: s\nsubSuites:\n  - name: log in\n    phase: setup\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("phase");
    }

    // ---------- anchors, aliases, merge keys ----------

    @Test
    void load_resolvesAnchorsAndAliases() {
        // given - the inherited authoring pattern; the predecessor's own suites are built on it
        var yaml = """
                name: s
                constants:
                  base: &base http://localhost:8080
                  alsoBase: *base
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(suite.constants()).containsEntry("alsoBase", "http://localhost:8080");
    }

    @Test
    void load_mergingThenOverridingIsNotADuplicateKey() {
        // given - the whole point of a merge key; the explicit value must win
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition: &defaults
                      url: http://x
                      method: GET
                  - name: r2
                    requestDefinition:
                      <<: *defaults
                      method: POST
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(suite.requests()).hasSize(2);
    }

    // ---------- binding by protocol ----------

    @Test
    void load_bindsARequestDefinitionWithNoProtocolAsHttp() {
        // given - no HTTP suite gains a line for a key whose answer is the default
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x/y
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(suite.requests().getFirst().requestDefinition()).isInstanceOf(HttpRequestDefinition.class);
    }

    @Test
    void load_bindsTheProtocolTheBlockDeclares() {
        // given - a loader wired for two protocols, which is what a plugin's handler produces
        var loader = new SuiteLoader(Map.of(HTTP, HttpRequestDefinition.class, "stub", StubDefinition.class));
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      protocol: stub
                      target: somewhere
                """;

        // when
        var suite = loader.load(yaml);

        // then
        assertThat(suite.requests().getFirst().requestDefinition()).isInstanceOf(StubDefinition.class);
    }

    @Test
    void load_consumesProtocolRatherThanBindingIt() {
        // given - no definition type declares the key, and unknown keys are a load error, so a
        // protocol that reached the bound type would fail every request that named one
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      protocol: http
                      url: http://x/y
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(((HttpRequestDefinition) suite.requests().getFirst().requestDefinition()).getUrl())
                .isEqualTo("http://x/y");
    }

    @Test
    void load_rejectsAProtocolThisLoaderHasNoClassFor() {
        // given - authorable is the same set as executable, so this fails at load rather than at a cast
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      protocol: ftp
                      url: http://x/y
                """;

        // then - naming what this loader knows, since a hand-built one may know less than its runner
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ftp")
                .hasMessageContaining("http");
    }

    @Test
    void load_rejectsARequestDefinitionThatIsNotAMapping() {
        // given - the block is where a protocol is read from, so it has to be one before anything else
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition: just a string
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void load_rejectsAProtocolThatIsNotAString() {
        // given
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      protocol: [http]
                      url: http://x/y
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_keepsThePositionOfAFailureInsideARequestDefinition() {
        // given - the deserializer runs inside the one convertValue call precisely so that Jackson's
        // own path still produces the pointer the position index is keyed by
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x/y
                      headers: not a mapping
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("headers")
                .hasMessageContaining("line 6");
    }

    @Test
    void load_keepsThePositionOfAnUnknownKeyInsideARequestDefinition() {
        // given - the chosen type's own unknown-key check must survive the delegation
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x/y
                      methd: GET
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("methd")
                .hasMessageContaining("line 6");
    }

    @Test
    void constructor_rejectsNullProtocols() {
        assertThatThrownBy(() -> new SuiteLoader(null)).isInstanceOf(BratException.class);
    }

    /** A definition of a protocol core knows nothing about, to prove the lookup is not hardcoded. */
    public static final class StubDefinition extends ConfigData implements RequestDefinition {

        private final String target;

        @JsonCreator
        public StubDefinition(String target) {
            super(null);
            this.target = target;
        }

        public String getTarget() {
            return target;
        }

        @Override
        public String protocol() {
            return "stub";
        }
    }

    // ---------- structural rejections ----------

    @Test
    void load_rejectsATokenInConstantsNamingTheOriginAndTheKey() {
        // given - a ${...} in a constant would never be resolved, so it is refused rather than kept as text
        var yaml = """
                name: s
                constants:
                  db:
                    url: "${env.host}/db"
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml, "orders.brat.yaml"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("orders.brat.yaml")
                .hasMessageContaining("constants.db.url")
                .hasMessageNotContaining("env.host");
    }

    @Test
    void load_rejectsADuplicateKeyNamingItAndItsPosition() {
        // given
        var yaml = "name: s\ndescription: one\ndescription: two\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("description")
                .hasMessageContaining("line 3");
    }

    @Test
    void load_rejectsAnUnknownKey() {
        // given - binding nothing silently would let the request run with no assertions and pass green
        var yaml = """
                name: s
                requests:
                  - name: r
                    responsActions:
                      assertions: []
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("responsActions")
                .hasMessageContaining("line 4");
    }

    @Test
    void load_rejectsATimeoutInsideTheRequestDefinition() {
        // given - where timeout lived before it moved to the request; loading it would leave it read
        // by nothing, and the request would run on the default
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x
                      timeout: "5000"
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void load_rejectsAuthInsideTheRequestDefinition() {
        // given
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x
                      auth:
                        type: none
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("auth");
    }

    @Test
    void load_rejectsAuthOnARequest() {
        // given - nothing reads a request's auth yet, so rejecting it beats binding it inert
        var yaml = """
                name: s
                requests:
                  - name: r
                    auth:
                      type: none
                    requestDefinition:
                      url: http://x
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("auth");
    }

    @Test
    void load_bindsATimeoutOnTheRequest() {
        // given
        var yaml = """
                name: s
                requests:
                  - name: r
                    timeout: "${vars.t}"
                    requestDefinition:
                      url: http://x
                """;

        // when
        var suite = underTest.load(yaml);

        // then
        assertThat(suite.requests().getFirst().timeout()).isEqualTo("${vars.t}");
    }

    @Test
    void load_rejectsAValueOfTheWrongShape() {
        // given - a sequence where a single value belongs; the shape is the loader's business, the
        // value is not
        var yaml = """
                name: s
                requests:
                  - name: r
                    flowControl:
                      repeatUntil:
                        maxAttempts: [1, 2]
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("maxAttempts")
                .hasMessageContaining("line 6");
    }

    @Test
    void load_rejectsAValueOutsideItsEnumWithoutQuotingIt() {
        // given - Jackson's own message quotes the value back, which a suite file may not survive
        var secret = "s3cr3t-token-value";

        // then - the accepted values are safe to print, the authored one is not
        assertThatThrownBy(() -> underTest.load("name: s\nrequests:\n  - name: r\n    phase: " + secret + "\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("phase")
                .hasMessageContaining("SETUP")
                .hasMessageNotContaining(secret);
    }

    @Test
    void load_reportsAFailureRaisedWhileBindingAValue() {
        // given - two spellings of one HTTP header; the data type rejects it in its constructor, and
        // only the loader can say where it was written
        var yaml = """
                name: s
                requests:
                  - name: r
                    requestDefinition:
                      url: http://x
                      headers:
                        Content-Type: application/json
                        content-type: text/plain
                """;

        // then - the constructor's own words, not Jackson's wrapper around them
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("content-type")
                .hasMessageContaining("line");
    }

    @Test
    void load_rejectsAnEmptyDocument() {
        assertThatThrownBy(() -> underTest.load("")).isInstanceOf(BratException.class);
    }

    @Test
    void load_rejectsADocumentThatIsNotAMapping() {
        assertThatThrownBy(() -> underTest.load("- one\n- two\n")).isInstanceOf(BratException.class);
    }

    @Test
    void load_rejectsMalformedYaml() {
        assertThatThrownBy(() -> underTest.load("name: [unclosed\n")).isInstanceOf(BratException.class);
    }

    // ---------- addressability ----------

    @Test
    void load_rejectsASuiteWithoutAName() {
        assertThatThrownBy(() -> underTest.load("constants:\n  x: 1\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("name");
    }

    @Test
    void load_rejectsARequestWithoutAName() {
        // given
        var yaml = "name: s\nrequests:\n  - id: r1\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("name");
    }

    @Test
    void load_rejectsASlashInAName() {
        // given - the path separator; a name holding one would make an address ambiguous
        var yaml = "name: happy/path\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("/");
    }

    @Test
    void load_rejectsTwoSiblingsSharingAName() {
        // given - two nodes would share one address
        var yaml = """
                name: s
                requests:
                  - name: create
                  - name: create
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("create");
    }

    @Test
    void load_rejectsARequestAndASubSuiteSharingAName() {
        // given - both contribute a path segment, so both would address as s/login
        var yaml = """
                name: s
                requests:
                  - name: login
                subSuites:
                  - name: login
                """;

        // then - the sibling namespace is shared across the two kinds, not one per list
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("login");
    }

    @Test
    void load_rejectsTwoSubSuitesSharingAName() {
        // given
        var yaml = """
                name: s
                subSuites:
                  - name: a
                  - name: a
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("a");
    }

    @Test
    void load_allowsTheSameNameUnderDifferentParents() {
        // given - siblings must differ; cousins need not, since the path disambiguates them
        var yaml = """
                name: s
                subSuites:
                  - name: a
                    requests:
                      - name: create
                  - name: b
                    requests:
                      - name: create
                """;

        // then
        assertThatCode(() -> underTest.load(yaml)).doesNotThrowAnyException();
    }

    @Test
    void load_rejectsTwoRequestsSharingAnId() {
        // given - a duplicate id is undetectable at any later point of use
        var yaml = """
                name: s
                subSuites:
                  - name: a
                    requests:
                      - name: one
                        id: shared
                  - name: b
                    requests:
                      - name: two
                        id: shared
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("shared");
    }

    @Test
    void load_rejectsConstantsOnASubSuite() {
        // given - the namespace is flat, so a nested constant would leak to every later sibling
        var yaml = """
                name: s
                subSuites:
                  - name: a
                    constants:
                      x: 1
                """;

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants");
    }

    @Test
    void load_allowsConstantsOnTheRootSuite() {
        // then
        assertThatCode(() -> underTest.load("name: s\nconstants:\n  x: 1\n")).doesNotThrowAnyException();
    }

    // ---------- required nested values ----------

    @Test
    void load_rejectsARepeatUntilWithoutACondition() {
        // given - "repeat until" with nothing to wait for is not "repeat N times"
        var yaml = """
                name: s
                requests:
                  - name: r
                    flowControl:
                      repeatUntil:
                        maxAttempts: 10
                """;

        // then - the interpolator rejects this too, but only the loader can say which line
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("condition")
                .hasMessageContaining("line");
    }

    @Test
    void load_allowsAFlowControlWithoutARepeatUntil() {
        // given - pacing a request is not repeating it, so there is nothing to require
        var yaml = """
                name: s
                requests:
                  - name: r
                    flowControl:
                      waitAfter: "500"
                """;

        // then
        assertThatCode(() -> underTest.load(yaml)).doesNotThrowAnyException();
    }

    // ---------- what an error may say ----------

    @Test
    void load_neverEchoesAValue() {
        // given - a suite file may hold a literal credential, and an error travels into CI logs
        var secret = "s3cr3t-token-value";
        var yaml = "name: s\nunknownKey: " + secret + "\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("unknownKey")
                .hasMessageNotContaining(secret);
    }

    @Test
    void load_namesTheOriginWhenGivenOne() {
        // given
        var yaml = "name: s\nunknownKey: x\n";

        // then
        assertThatThrownBy(() -> underTest.load(yaml, "order-api.yaml"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("order-api.yaml");
    }
}

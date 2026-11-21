package dev.pbroman.brat.core.loader;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.ParserException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlComposerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void compose_convertsAMappingDocument() {
        // when
        var document = YamlComposer.compose("name: s\nphase: setup\n", YamlLimits.SUITE);

        // then
        assertThat(document.root()).isInstanceOf(Map.class);
        assertThat(asMap(document.root())).containsEntry("name", "s");
    }

    @Test
    void compose_indexesPositionsByJsonPointer() {
        // given - the pointer spelling Jackson uses for its own error paths
        var yaml = """
                name: s
                requests:
                  - name: create
                """;

        // when
        var document = YamlComposer.compose(yaml, YamlLimits.SUITE);

        // then
        assertThat(document.positions().at("/requests/0/name"))
                .hasValueSatisfying(position -> assertThat(position.line()).isEqualTo(3));
    }

    @Test
    void compose_returnsAnEmptyPositionForAPointerItDoesNotHold() {
        // when
        var document = YamlComposer.compose("name: s\n", YamlLimits.SUITE);

        // then - a caller must be able to report an error it cannot place
        assertThat(document.positions().at("/nothing/here")).isEmpty();
        assertThat(document.positions().at(null)).isEmpty();
    }

    @Test
    void compose_resolvesMergeKeysBeforeTheDuplicateCheck() {
        // given - compose flattens `<<:` with the explicit key winning, so this is one `method`, not two
        var yaml = """
                defaults: &defaults
                  method: GET
                request:
                  <<: *defaults
                  method: POST
                """;

        // when
        var document = YamlComposer.compose(yaml, YamlLimits.SUITE);

        // then
        assertThat(asMap(asMap(document.root()).get("request"))).containsEntry("method", "POST");
    }

    @Test
    void compose_typesAScalarByItsTag() {
        // given - the resolver's view, which is the one Jackson's own YAML parser would have had
        var yaml = """
                text: hello
                number: 42
                fraction: 1.5
                flag: true
                nothing:
                """;

        // when
        var root = asMap(YamlComposer.compose(yaml, YamlLimits.SUITE).root());

        // then - BigDecimal rather than Long/Double: every value reaches BRAT as text anyway
        assertThat(root.get("text")).isEqualTo("hello");
        assertThat(root.get("number")).isEqualTo(new BigDecimal("42"));
        assertThat(root.get("fraction")).isEqualTo(new BigDecimal("1.5"));
        assertThat(root.get("flag")).isEqualTo(Boolean.TRUE);
        assertThat(root).containsEntry("nothing", null);
    }

    @Test
    void compose_leavesTheYamlOneOnlyBooleansAsStrings() {
        // given - CoreSchema is YAML 1.2, whose BOOL is true/false only; `no` is a country code
        var root = asMap(YamlComposer.compose("country: no\nswitch: off\n", YamlLimits.SUITE)
                .root());

        // then
        assertThat(root.get("country")).isEqualTo("no");
        assertThat(root.get("switch")).isEqualTo("off");
    }

    @Test
    void compose_rejectsADuplicateKeyInOneMapping() {
        // given - compose does not check this; the construct step we skip is what would have
        assertThatThrownBy(() -> YamlComposer.compose("a: 1\na: 2\n", YamlLimits.SUITE))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("a");
    }

    @Test
    void compose_rejectsAnEmptyDocument() {
        assertThatThrownBy(() -> YamlComposer.compose("", YamlLimits.SUITE)).isInstanceOf(BratException.class);
    }

    @Test
    void compose_rejectsADocumentOfNothingButComments() {
        // given - text, so not blank, and no node at all once composed
        assertThatThrownBy(() -> YamlComposer.compose("# a suite used to live here\n", YamlLimits.SUITE))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void compose_rejectsMalformedYaml() {
        assertThatThrownBy(() -> YamlComposer.compose("a: [unclosed\n", YamlLimits.SUITE))
                .isInstanceOf(BratException.class);
    }

    @Test
    void compose_reportsAnEngineLimitInItsOwnWords() {
        // given - a generated suite hitting 3 MB is a plausible accident, not a corner case
        var huge = "name: " + "x".repeat(4 * 1024 * 1024) + "\n";

        // then - BRAT's own words, not a digit that also occurs in the engine's byte count
        assertThatThrownBy(() -> YamlComposer.compose(huge, YamlLimits.SUITE))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("exceeds the 3 MB limit");
    }

    @Test
    void compose_reportsTheAliasLimitInItsOwnWords() {
        // given - 51 aliases to a mapping; the limit counts aliases to collections, not to scalars
        var yaml = "anchored: &a\n  k: v\naliases:\n" + "  - *a\n".repeat(51);

        // then
        assertThatThrownBy(() -> YamlComposer.compose(yaml, YamlLimits.SUITE))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("50");
    }

    // ---------- limits and the document's name ----------

    private static final YamlLimits SMALL = new YamlLimits("The spec", 1, 2);

    @Test
    void compose_readsWithinTheLimitsItIsGiven() {
        // given - 3 aliases to a mapping against a limit of 2
        var yaml = "anchored: &a\n  k: v\naliases:\n" + "  - *a\n".repeat(3);

        // then
        assertThatThrownBy(() -> YamlComposer.compose(yaml, SMALL))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The spec uses more than the 2 permitted aliases");
    }

    @Test
    void compose_namesTheSizeLimitItWasGiven() {
        // given - just over one megabyte
        var yaml = "name: " + "x".repeat(1024 * 1024) + "\n";

        // then
        assertThatThrownBy(() -> YamlComposer.compose(yaml, SMALL))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The spec exceeds the 1 MB limit");
    }

    @Test
    void compose_namesTheDocumentWhenItIsEmptyOrInvalid() {
        // then
        assertThatThrownBy(() -> YamlComposer.compose("", SMALL)).hasMessage("The spec is empty");
        assertThatThrownBy(() -> YamlComposer.compose("# nothing\n", SMALL)).hasMessage("The spec is empty");
        assertThatThrownBy(() -> YamlComposer.compose("a: [unclosed\n", SMALL))
                .hasMessageStartingWith("The spec is not valid YAML: ");
    }

    @Test
    void compose_namesTheProblemAndItsPositionButQuotesNothing() {
        // given - the engine would quote the offending line beneath its reason
        var yaml = "name: s\ntoken: [s3cr3t\nnext: 1\n";

        // then
        assertThatThrownBy(() -> YamlComposer.compose(yaml, SMALL))
                .hasMessageStartingWith("The spec is not valid YAML: ")
                .hasMessageContaining(" at line ")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void compose_resolvesAliasesAndMergeKeys() {
        // given
        var yaml = """
                base: &base
                  in: path
                  required: true
                reused: *base
                merged:
                  <<: *base
                  name: id
                """;

        // when
        var root = asMap(YamlComposer.compose(yaml, SMALL).root());

        // then
        assertThat(asMap(root.get("reused"))).containsEntry("in", "path");
        assertThat(asMap(root.get("merged")))
                .containsEntry("in", "path")
                .containsEntry("required", true)
                .containsEntry("name", "id")
                .doesNotContainKey("<<");
    }

    /** A mark over a document holding a secret, so a quoted excerpt would show it. */
    private static Optional<Mark> markInSecret() {
        var text = "token: s3cr3t\n";
        return Optional.of(new Mark("doc", 7, 0, 7, text.codePoints().toArray(), 7));
    }

    @Test
    void reasonOf_namesTheProblemAndWhereItSitsWithoutQuoting() {
        // given
        var failure = new ParserException("while parsing", markInSecret(), "expected a value", markInSecret());

        // when
        var reason = YamlComposer.reasonOf(failure);

        // then
        assertThat(reason).isEqualTo("expected a value at line 1, column 8");
        assertThat(failure.getMessage()).contains("s3cr3t");
    }

    @Test
    void reasonOf_fallsBackToTheContextWithoutQuoting() {
        // given
        var failure = new ParserException("while parsing a mapping", markInSecret(), null, Optional.empty());

        // then
        assertThat(YamlComposer.reasonOf(failure)).isEqualTo("while parsing a mapping at line 1, column 8");
    }

    @Test
    void reasonOf_saysSoWhenAMarkedFailureGivesNoReason() {
        // given
        var failure = new ParserException(null, markInSecret(), null, markInSecret());

        // then
        assertThat(YamlComposer.reasonOf(failure)).isEqualTo("no reason given");
    }

    @Test
    void reasonOf_takesAnUnmarkedFailuresMessageOrNothing() {
        // then
        assertThat(YamlComposer.reasonOf(new YamlEngineException("limit reached")))
                .isEqualTo("limit reached");
        assertThat(YamlComposer.reasonOf(new YamlEngineException((String) null)))
                .isEmpty();
    }

    @Test
    void compose_throwsForNullLimits() {
        assertThatThrownBy(() -> YamlComposer.compose("a: 1\n", null)).isInstanceOf(BratException.class);
    }

    @Test
    void tree_returnsTheRootValueAlone() {
        // when
        var tree = YamlComposer.tree("name: s\nlist: [1, 2]\n", YamlLimits.SUITE);

        // then
        assertThat(asMap(tree)).containsEntry("name", "s").containsKey("list");
    }

    @Test
    void tree_throwsAsComposeDoes() {
        assertThatThrownBy(() -> YamlComposer.tree("a: 1\na: 2\n", SMALL))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Duplicate key 'a'");
    }

    @Test
    void suiteLimits_areThreeMegabytesAndFiftyAliases() {
        assertThat(YamlLimits.SUITE).isEqualTo(new YamlLimits("The suite document", 3, 50));
    }

    @Test
    void yamlLimits_rejectABlankNameOrANonPositiveLimit() {
        assertThatThrownBy(() -> new YamlLimits(" ", 1, 1)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new YamlLimits(null, 1, 1)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new YamlLimits("The spec", 0, 1)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new YamlLimits("The spec", 1, 0)).isInstanceOf(BratException.class);
    }
}

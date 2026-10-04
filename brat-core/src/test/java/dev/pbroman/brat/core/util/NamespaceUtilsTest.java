package dev.pbroman.brat.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class NamespaceUtilsTest {

    @Test
    void flattenLiteral_keepsAFlatMapAsItIs() {
        // given
        var values = Map.<String, Object>of("baseUrl", "http://localhost", "retries", 3);

        // when
        var result = NamespaceUtils.flattenLiteral(values, "env");

        // then
        assertThat(result).containsOnly(entry("baseUrl", "http://localhost"), entry("retries", 3));
    }

    @Test
    void flattenLiteral_readsANestedMapAsDottedKeysToAnyDepth() {
        // given
        var values = Map.<String, Object>of("db", Map.of("primary", Map.of("host", "h1", "port", 5432)));

        // when
        var result = NamespaceUtils.flattenLiteral(values, "env");

        // then
        assertThat(result).containsOnly(entry("db.primary.host", "h1"), entry("db.primary.port", 5432));
    }

    @Test
    void flattenLiteral_usesANonStringNestedKeyByItsStringValue() {
        // given - YAML 'ports: {8080: http}' can reach a map with a non-String key
        var values = Map.<String, Object>of("ports", Map.of(8080, "http"));

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).containsOnly(entry("ports.8080", "http"));
    }

    @Test
    void flattenLiteral_usesANullKeyAsTheTextNull() {
        // given - only a hand-built map can carry one; YAML cannot
        var values = new HashMap<String, Object>();
        values.put(null, "x");

        // when
        var result = NamespaceUtils.flattenLiteral(values, "env");

        // then
        assertThat(result).containsOnly(entry("null", "x"));
    }

    @Test
    void flattenLiteral_dropsAnEmptyNestedMap() {
        // given
        var values = Map.<String, Object>of("db", Map.of(), "name", "orders");

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).containsOnly(entry("name", "orders"));
    }

    @Test
    void flattenLiteral_keepsAListAsTheSameInstanceAndDoesNotFlattenInsideIt() {
        // given
        var list = List.<Object>of("a", Map.of("k", "v"));
        var values = Map.<String, Object>of("items", list);

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).containsOnlyKeys("items");
        assertThat(result.get("items")).isSameAs(list);
    }

    @Test
    void flattenLiteral_keepsTheOrderOfItsInputWithNestedEntriesInPlace() {
        // given
        var nested = new LinkedHashMap<String, Object>();
        nested.put("host", "h1");
        nested.put("port", "5432");
        var values = new LinkedHashMap<String, Object>();
        values.put("first", "1");
        values.put("db", nested);
        values.put("last", "2");

        // when
        var result = NamespaceUtils.flattenLiteral(values, "env");

        // then
        assertThat(result.keySet()).containsExactly("first", "db.host", "db.port", "last");
    }

    @Test
    void flattenLiteral_keepsANullValueUnderItsKey() {
        // given - 'region:' with nothing after it; absent is the lookup's call, not this one's
        var values = new HashMap<String, Object>();
        values.put("region", null);

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).containsEntry("region", null);
    }

    @Test
    void flattenLiteral_doesNotRejectABlankKey() {
        // given
        var values = Map.<String, Object>of(" ", "x");

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).containsOnly(entry(" ", "x"));
    }

    @Test
    void flattenLiteral_returnsAnEmptyMapForAnEmptyInput() {
        // when
        var result = NamespaceUtils.flattenLiteral(Map.of(), "env");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void flattenLiteral_returnsAnUnmodifiableMap() {
        // given
        var result = NamespaceUtils.flattenLiteral(Map.of("a", "1"), "env");

        // then
        assertThatThrownBy(() -> result.put("b", "2")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void flattenLiteral_doesNotModifyItsInput() {
        // given
        var nested = new HashMap<String, Object>(Map.of("host", "h1"));
        var values = new HashMap<String, Object>(Map.of("db", nested));

        // when
        NamespaceUtils.flattenLiteral(values, "env");

        // then
        assertThat(values).containsOnly(entry("db", nested));
        assertThat(nested).containsOnly(entry("host", "h1"));
    }

    @Test
    void flattenLiteral_throwsForNullValues() {
        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(null, "env")).isInstanceOf(BratException.class);
    }

    @Test
    void flattenLiteral_throwsForANullNamespace() {
        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(Map.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void flattenLiteral_rejectsANestedPathCollidingWithADottedKeyNamingItButNotTheValues() {
        // given
        var values = new LinkedHashMap<String, Object>();
        values.put("db", Map.of("host", "nested-value"));
        values.put("db.host", "dotted-value");

        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(values, "constants"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.db.host")
                .hasMessageNotContaining("nested-value")
                .hasMessageNotContaining("dotted-value");
    }

    @Test
    void flattenLiteral_rejectsATokenInAValueNamingTheKeyButNotTheValue() {
        // given
        var values = Map.<String, Object>of("ordersUrl", "${env.baseUrl}/orders");

        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(values, "env"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.ordersUrl")
                .hasMessageNotContaining("baseUrl")
                .hasMessageNotContaining("/orders");
    }

    @Test
    void flattenLiteral_rejectsATokenInANestedValueNamingTheFullDottedKey() {
        // given
        var values = Map.<String, Object>of("db", Map.of("password", "s3cr3t-${x}"));

        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(values, "constants"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.db.password")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void flattenLiteral_rejectsATokenInAListElement() {
        // given
        var values = Map.<String, Object>of("hosts", List.of("a", "${env.b}"));

        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(values, "constants"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.hosts");
    }

    @Test
    void flattenLiteral_rejectsATokenInsideAMapWithinAList() {
        // given
        var inner = new ArrayList<Object>();
        inner.add(Map.of("k", "${__uuid}"));
        var values = Map.<String, Object>of("items", inner);

        // then
        assertThatThrownBy(() -> NamespaceUtils.flattenLiteral(values, "constants"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.items");
    }

    @Test
    void flattenLiteral_acceptsADollarOrABraceThatIsNotAToken() {
        // given - only the two characters together open a token
        var values = Map.<String, Object>of("price", "$5", "template", "{name}", "split", "$ {x}");

        // when
        var result = NamespaceUtils.flattenLiteral(values, "constants");

        // then
        assertThat(result).hasSize(3);
    }
}

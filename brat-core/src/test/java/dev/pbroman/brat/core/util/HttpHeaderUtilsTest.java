package dev.pbroman.brat.core.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpHeaderUtilsTest {

    @Test
    void get_findsAHeaderWhateverCaseItWasAuthoredIn() {
        // given
        var headers = Map.of("content-type", "application/json");

        // when / then
        assertThat(HttpHeaderUtils.get(headers, "Content-Type")).isEqualTo("application/json");
        assertThat(HttpHeaderUtils.get(headers, "CONTENT-TYPE")).isEqualTo("application/json");
        assertThat(HttpHeaderUtils.get(headers, "content-type")).isEqualTo("application/json");
    }

    @Test
    void get_returnsNullForNullHeaders() {
        // when / then
        assertThat(HttpHeaderUtils.get(null, "Content-Type")).isNull();
    }

    @Test
    void get_returnsNullForANullName() {
        // when / then
        assertThat(HttpHeaderUtils.get(Map.of("Accept", "*/*"), null)).isNull();
    }

    @Test
    void get_returnsNullWhenNoEntryMatches() {
        // when / then
        assertThat(HttpHeaderUtils.get(Map.of("Accept", "*/*"), "Content-Type")).isNull();
    }

    @Test
    void get_returnsNullForAMatchingEntryWithANullValue() {
        // given — YAML's `X-Foo:` with nothing after it
        var headers = new HashMap<String, String>();
        headers.put("X-Foo", null);

        // then
        assertThat(HttpHeaderUtils.get(headers, "x-foo")).isNull();
    }

    @Test
    void contains_isTrueForAMatchingEntryWithANullValue() {
        // given
        var headers = new HashMap<String, String>();
        headers.put("X-Foo", null);

        // then — this is what separates contains from get
        assertThat(HttpHeaderUtils.contains(headers, "x-foo")).isTrue();
        assertThat(HttpHeaderUtils.get(headers, "x-foo")).isNull();
    }

    @Test
    void contains_isFalseForNullHeadersOrNullName() {
        // when / then
        assertThat(HttpHeaderUtils.contains(null, "Accept")).isFalse();
        assertThat(HttpHeaderUtils.contains(Map.of("Accept", "*/*"), null)).isFalse();
    }

    @Test
    void get_toleratesANullKeyInTheMap() {
        // given - String.equalsIgnoreCase null-checks its argument but not its receiver
        var headers = new HashMap<String, String>();
        headers.put(null, "application/json");
        headers.put("Accept", "*/*");

        // then
        assertThat(HttpHeaderUtils.get(headers, "Accept")).isEqualTo("*/*");
        assertThat(HttpHeaderUtils.get(headers, "Content-Type")).isNull();
    }

    @Test
    void contains_toleratesANullKeyInTheMap() {
        // given
        var headers = new HashMap<String, String>();
        headers.put(null, "application/json");

        // then
        assertThat(HttpHeaderUtils.contains(headers, "Accept")).isFalse();
    }

    @Test
    void requireNoCaseDuplicates_passesForDistinctNames() {
        // given
        var headers = Map.of("Content-Type", "application/json", "Accept", "*/*");

        // then
        assertThatCode(() -> HttpHeaderUtils.requireNoCaseDuplicates(headers)).doesNotThrowAnyException();
    }

    @Test
    void requireNoCaseDuplicates_throwsNamingBothSpellings() {
        // given - one header to HTTP, two keys to YAML, so the loader's duplicate check misses it
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("content-type", "text/plain");

        // then
        assertThatThrownBy(() -> HttpHeaderUtils.requireNoCaseDuplicates(headers))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Content-Type")
                .hasMessageContaining("content-type");
    }

    @Test
    void requireNoCaseDuplicates_passesForNullOrEmpty() {
        // when / then
        assertThatCode(() -> HttpHeaderUtils.requireNoCaseDuplicates(null)).doesNotThrowAnyException();
        assertThatCode(() -> HttpHeaderUtils.requireNoCaseDuplicates(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void requireNoCaseDuplicates_ignoresANullKey() {
        // given
        var headers = new HashMap<String, String>();
        headers.put(null, "application/json");
        headers.put("Accept", "*/*");

        // then
        assertThatCode(() -> HttpHeaderUtils.requireNoCaseDuplicates(headers)).doesNotThrowAnyException();
    }
}

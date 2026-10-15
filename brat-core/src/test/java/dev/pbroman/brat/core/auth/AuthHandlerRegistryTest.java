package dev.pbroman.brat.core.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthHandlerRegistryTest {

    @Test
    void get_returnsTheHandlerRegisteredForTheType() {
        // given
        var basic = new BasicAuthHandler();
        var underTest = new AuthHandlerRegistry(List.of(new NoneAuthHandler(), basic));

        // when
        var found = underTest.get("basic");

        // then
        assertThat(found).isSameAs(basic);
    }

    @Test
    void get_matchesTheTypeExactlyCaseIncluded() {
        // given
        var underTest = new AuthHandlerRegistry(List.of(new BasicAuthHandler()));

        // when / then
        assertThatThrownBy(() -> underTest.get("Basic")).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_letsALaterHandlerReplaceAnEarlierOneOfTheSameType() {
        // given - a plugin overriding a built-in comes after it
        var replacement = new FixedTypeHandler("basic");

        // when
        var underTest = new AuthHandlerRegistry(List.of(new BasicAuthHandler(), replacement));

        // then
        assertThat(underTest.get("basic")).isSameAs(replacement);
        assertThat(underTest.types()).containsExactly("basic");
    }

    @Test
    void get_namesTheTypeAndEveryRegisteredTypeSortedWhenNoneMatches() {
        // given - registered out of order
        var underTest = new AuthHandlerRegistry(
                List.of(new NoneAuthHandler(), new BearerAuthHandler(), new ApiKeyAuthHandler()));

        // when / then
        assertThatThrownBy(() -> underTest.get("aws-sigv4"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("aws-sigv4")
                .hasMessageContaining("[apikey, bearer, none]");
    }

    @Test
    void get_throwsOnANullType() {
        // given
        var underTest = new AuthHandlerRegistry(List.of(new NoneAuthHandler()));

        // when / then
        assertThatThrownBy(() -> underTest.get(null)).isInstanceOf(BratException.class);
    }

    @Test
    void types_areSorted() {
        // given
        var underTest = new AuthHandlerRegistry(List.of(
                new NoneAuthHandler(), new BasicAuthHandler(), new BearerAuthHandler(), new ApiKeyAuthHandler()));

        // when
        var types = underTest.types();

        // then
        assertThat(types).containsExactly("apikey", "basic", "bearer", "none");
    }

    @Test
    void types_isUnmodifiable() {
        // given
        var underTest = new AuthHandlerRegistry(List.of(new NoneAuthHandler()));

        // when / then
        assertThatThrownBy(() -> underTest.types().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> underTest.types().remove(NoneAuthHandler.TYPE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(underTest.get(NoneAuthHandler.TYPE)).isInstanceOf(NoneAuthHandler.class);
    }

    @Test
    void types_isEmptyForARegistryOverNoHandlers() {
        // when
        var underTest = new AuthHandlerRegistry(List.of());

        // then
        assertThat(underTest.types()).isNotNull().isEmpty();
    }

    @Test
    void constructor_doesNotRetainTheCollection() {
        // given
        var handlers = new ArrayList<AuthHandler>(List.of(new NoneAuthHandler()));
        var underTest = new AuthHandlerRegistry(handlers);

        // when
        handlers.add(new BasicAuthHandler());

        // then
        assertThat(underTest.types()).containsExactly("none");
    }

    @Test
    void constructor_throwsOnANullCollection() {
        // when / then
        assertThatThrownBy(() -> new AuthHandlerRegistry(null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnANullHandler() {
        // given
        var handlers = Arrays.<AuthHandler>asList(new NoneAuthHandler(), null);

        // when / then
        assertThatThrownBy(() -> new AuthHandlerRegistry(handlers)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnANullTypeNamingTheClass() {
        // given
        var handlers = List.<AuthHandler>of(new FixedTypeHandler(null));

        // when / then
        assertThatThrownBy(() -> new AuthHandlerRegistry(handlers))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(FixedTypeHandler.class.getName());
    }

    @Test
    void constructor_throwsOnABlankTypeNamingTheClass() {
        // given
        var handlers = List.<AuthHandler>of(new FixedTypeHandler(" "));

        // when / then
        assertThatThrownBy(() -> new AuthHandlerRegistry(handlers))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(FixedTypeHandler.class.getName());
    }

    /** A handler with whatever type it is given, adding no headers. */
    private static final class FixedTypeHandler implements AuthHandler {

        private final String type;

        private FixedTypeHandler(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
            return Map.of();
        }
    }
}

package dev.pbroman.brat.core.runner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.TestSuite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InheritedDefaultsTest {

    // ---------- NONE and the constructor ----------

    @Test
    void none_inheritsNothing() {
        // then
        assertThat(InheritedDefaults.NONE.timeout()).isNull();
        assertThat(InheritedDefaults.NONE.requestHandlers()).isEmpty();
    }

    @Test
    void constructor_defaultsANullHandlerMapToEmpty() {
        // when
        var defaults = new InheritedDefaults(null, null);

        // then
        assertThat(defaults.requestHandlers()).isNotNull().isEmpty();
    }

    @Test
    void constructor_copiesTheHandlerMapAndHandsOutAnUnmodifiableOne() {
        // given
        var names = new HashMap<String, String>();
        names.put("http", "plain");

        // when
        var defaults = new InheritedDefaults(null, names);
        names.put("ftp", "passive");

        // then
        assertThat(defaults.requestHandlers()).containsOnlyKeys("http");
        assertThatThrownBy(() -> defaults.requestHandlers().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- with ----------

    @Test
    void with_takesTheSuitesTimeout() {
        // when
        var folded = new InheritedDefaults("1000", Map.of()).with(suite("2000", Map.of()));

        // then
        assertThat(folded.timeout()).isEqualTo("2000");
    }

    @Test
    void with_keepsTheInheritedTimeoutWhenTheSuiteDeclaresNone() {
        // when
        var folded = new InheritedDefaults("1000", Map.of()).with(suite(null, Map.of()));

        // then
        assertThat(folded.timeout()).isEqualTo("1000");
    }

    @Test
    void with_letsASuiteOptBackOutOfAnInheritedTimeoutWithDefault() {
        // given
        var inherited = new InheritedDefaults("1000", Map.of());

        // when
        var options = inherited.with(suite("default", Map.of())).options(request(null, Map.of()));

        // then
        assertThat(options.getTimeout()).isEqualTo("default");
        assertThat(options.timeoutMs()).isEqualTo(30000L);
    }

    @Test
    void with_treatsABlankTimeoutAsADeclaration() {
        // given - blank means "the default", which must be able to cancel an inherited timeout
        var inherited = new InheritedDefaults("1000", Map.of());

        // when
        var folded = inherited.with(suite("", Map.of()));

        // then
        assertThat(folded.timeout()).isEmpty();
    }

    @Test
    void with_takesATokenTimeoutAsDeclaredWithoutResolvingIt() {
        // when
        var folded = InheritedDefaults.NONE.with(suite("${vars.t}", Map.of()));

        // then
        assertThat(folded.timeout()).isEqualTo("${vars.t}");
    }

    @Test
    void with_laysTheSuitesHandlerNamesOverTheInheritedOnesPerProtocol() {
        // given
        var inherited = new InheritedDefaults(null, Map.of("http", "plain", "ftp", "passive"));

        // when
        var folded = inherited.with(suite(null, Map.of("http", "mtls", "grpc", "tls")));

        // then - the suite wins on http, adds grpc, and keeps the ftp it does not mention
        assertThat(folded.requestHandlers())
                .containsExactlyInAnyOrderEntriesOf(Map.of("http", "mtls", "ftp", "passive", "grpc", "tls"));
    }

    @Test
    void with_leavesTheReceiverUnchanged() {
        // given
        var inherited = new InheritedDefaults("1000", Map.of("http", "plain"));

        // when
        inherited.with(suite("2000", Map.of("http", "mtls")));

        // then
        assertThat(inherited.timeout()).isEqualTo("1000");
        assertThat(inherited.requestHandlers()).containsExactlyEntriesOf(Map.of("http", "plain"));
    }

    // ---------- options ----------

    @Test
    void options_prefersTheRequestsOwnTimeout() {
        // when
        var options = new InheritedDefaults("1000", Map.of()).options(request("3000", Map.of()));

        // then
        assertThat(options.getTimeout()).isEqualTo("3000");
    }

    @Test
    void options_fallsBackToTheInheritedTimeout() {
        // when
        var options = new InheritedDefaults("1000", Map.of()).options(request(null, Map.of()));

        // then
        assertThat(options.getTimeout()).isEqualTo("1000");
    }

    @Test
    void options_treatsABlankRequestTimeoutAsADeclaration() {
        // when
        var options = new InheritedDefaults("1000", Map.of()).options(request("", Map.of()));

        // then - declared, so it wins, and reading it gives the default
        assertThat(options.getTimeout()).isEmpty();
        assertThat(options.timeoutMs()).isEqualTo(30000L);
    }

    @Test
    void options_leavesTheTimeoutNullWhenNothingDeclaresOne() {
        // when
        var options = InheritedDefaults.NONE.options(request(null, Map.of()));

        // then - the default is applied when the value is read, not baked in here
        assertThat(options.getTimeout()).isNull();
        assertThat(options.timeoutMs()).isEqualTo(30000L);
    }

    @Test
    void options_isNotYetInterpolated() {
        // when
        var options = new InheritedDefaults("${vars.t}", Map.of()).options(request(null, Map.of()));

        // then
        assertThat(options.isInterpolated()).isFalse();
        assertThat(options.getTimeout()).isEqualTo("${vars.t}");
    }

    // ---------- handlerNames ----------

    @Test
    void handlerNames_laysTheRequestsNamesOverTheInheritedOnesPerProtocol() {
        // given
        var defaults = new InheritedDefaults(null, Map.of("http", "plain", "ftp", "passive"));

        // when
        var names = defaults.handlerNames(request(null, Map.of("http", "mtls")));

        // then
        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of("http", "mtls", "ftp", "passive"));
    }

    @Test
    void handlerNames_isEmptyWhenNothingNamesAHandler() {
        // when
        var names = InheritedDefaults.NONE.handlerNames(request(null, Map.of()));

        // then
        assertThat(names).isNotNull().isEmpty();
    }

    @Test
    void handlerNames_leavesTheDefaultsUnchanged() {
        // given
        var defaults = new InheritedDefaults(null, Map.of("http", "plain"));

        // when
        defaults.handlerNames(request(null, Map.of("http", "mtls", "ftp", "passive")));

        // then
        assertThat(defaults.requestHandlers()).containsExactlyEntriesOf(Map.of("http", "plain"));
    }

    // ---------- helpers ----------

    private static TestSuite suite(String timeout, Map<String, String> requestHandlers) {
        return new TestSuite("s", null, null, null, null, timeout, null, null, requestHandlers, List.of(), null);
    }

    private static Request request(String timeout, Map<String, String> requestHandlers) {
        return new Request(
                "r",
                null,
                null,
                timeout,
                null,
                null,
                requestHandlers,
                new HttpRequestDefinition("http://localhost/x", "GET", null, null),
                null,
                null);
    }
}

package dev.pbroman.brat.core.data.runtime;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeDataCaptureTest {

    private RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setCurrentPath("happy path/create an order");
    }

    @Test
    void setResponseVars_replacesTheNamespaceRatherThanMergingIntoIt() {
        // given - an earlier request's response
        runtimeData.setResponseVars(Map.of("statusCode", 200, "body", "old"));

        // when
        runtimeData.setResponseVars(Map.of("statusCode", 404));

        // then - renewed per request, so nothing of the previous one survives
        assertThat(runtimeData.getResponseVars()).containsEntry("statusCode", 404);
        assertThat(runtimeData.getResponseVars()).doesNotContainKey("body");
    }

    @Test
    void setResponseVars_emptiesTheNamespaceForARequestWithNoResponse() {
        // given
        runtimeData.setResponseVars(Map.of("statusCode", 200));

        // when - a skipped or errored request has no response
        runtimeData.setResponseVars(Map.of());

        // then - a later ${response.*} must fail rather than answer from further back
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void setResponseVars_treatsNullAsNoResponse() {
        // given
        runtimeData.setResponseVars(Map.of("statusCode", 200));

        // when
        runtimeData.setResponseVars(null);

        // then
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void setResponseVars_copiesSoTheCallerMayReuseItsMap() {
        // given
        var source = new HashMap<String, Object>();
        source.put("statusCode", 200);

        // when
        runtimeData.setResponseVars(source);
        source.put("statusCode", 500);

        // then
        assertThat(runtimeData.getResponseVars()).containsEntry("statusCode", 200);
    }

    @Test
    void captureVar_setsTheVariable() {
        // when
        runtimeData.captureVar("orderId", "42");

        // then
        assertThat(runtimeData.getVars()).containsEntry("orderId", "42");
    }

    @Test
    void captureVar_clearsAnEarlierTombstone() {
        // given - a capture that failed, then one that succeeded
        runtimeData.captureFailed("orderId", "no such path");

        // when
        runtimeData.captureVar("orderId", "42");

        // then - an ordinary variable, not a poisoned one
        assertThat(runtimeData.getTombstone("orderId")).isNull();
        assertThat(runtimeData.getVars()).containsEntry("orderId", "42");
    }

    @Test
    void captureVar_throwsForANullName() {
        // when / then
        assertThatThrownBy(() -> runtimeData.captureVar(null, "42")).isInstanceOf(BratException.class);
    }

    @Test
    void captureFailed_leavesATombstoneNamingTheCauseAndThePath() {
        // when
        runtimeData.captureFailed("orderId", "no such path");

        // then
        assertThat(runtimeData.getTombstone("orderId"))
                .isEqualTo(new CaptureTombstone("no such path", "happy path/create an order"));
    }

    @Test
    void captureFailed_removesAnyStaleValue() {
        // given - the key already holds a value captured by an earlier request
        runtimeData.captureVar("orderId", "41");

        // when
        runtimeData.captureFailed("orderId", "no such path");

        // then - the stale value must not be found ahead of the tombstone
        assertThat(runtimeData.getVars()).doesNotContainKey("orderId");
    }

    @Test
    void captureFailed_throwsForANullName() {
        // when / then
        assertThatThrownBy(() -> runtimeData.captureFailed(null, "why")).isInstanceOf(BratException.class);
    }

    @Test
    void getTombstone_answersNullForAKeyThatNeverFailed() {
        // given
        runtimeData.captureVar("orderId", "42");

        // when / then
        assertThat(runtimeData.getTombstone("orderId")).isNull();
        assertThat(runtimeData.getTombstone("neverMentioned")).isNull();
    }

    @Test
    void getTombstone_answersNullForNullRatherThanThrowing() {
        // when / then
        assertThat(runtimeData.getTombstone(null)).isNull();
    }
}

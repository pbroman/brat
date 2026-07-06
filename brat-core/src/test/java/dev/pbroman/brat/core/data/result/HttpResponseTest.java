package dev.pbroman.brat.core.data.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HttpResponseTest {

    @Test
    void headers_lookupIsCaseInsensitive() {
        // given
        var response = new HttpResponse(200, Map.of("Content-Type", List.of("application/json")), "{}");

        // when
        var values = response.headers().get("content-type");

        // then
        assertThat(values).containsExactly("application/json");
    }

    @Test
    void headers_preservesMultipleValuesForSameName() {
        // given
        var response = new HttpResponse(200, Map.of("Set-Cookie", List.of("a=1", "b=2")), null);

        // when
        var values = response.headers().get("set-cookie");

        // then
        assertThat(values).containsExactly("a=1", "b=2");
    }

    @Test
    void headers_defaultsToEmptyMapWhenNull() {
        // given
        var response = new HttpResponse(204, null, null);

        // then
        assertThat(response.headers()).isEmpty();
    }

    @Test
    void headers_isUnmodifiable() {
        // given
        var response = new HttpResponse(200, Map.of("X", List.of("y")), null);

        // when / then
        assertThatThrownBy(() -> response.headers().put("Z", List.of("w")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void headers_isUnmodifiableEvenWhenInputMapWasMutable() {
        // given
        var mutableHeaders = new HashMap<String, List<String>>();
        mutableHeaders.put("X", List.of("y"));
        var response = new HttpResponse(200, mutableHeaders, null);

        // when / then
        assertThatThrownBy(() -> response.headers().put("Z", List.of("w")))
                .isInstanceOf(UnsupportedOperationException.class);
        mutableHeaders.put("Z", List.of("w"));
        assertThat(response.headers()).doesNotContainKey("Z");
    }

    @Test
    void statusCodeAndBody_areStoredAsGiven() {
        // given
        var response = new HttpResponse(201, Map.of(), "created");

        // then
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("created");
    }

}

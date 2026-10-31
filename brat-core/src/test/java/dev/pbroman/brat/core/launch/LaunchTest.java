package dev.pbroman.brat.core.launch;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class LaunchTest {

    @Test
    void of_launchesASuiteWithNoDirectoryAndNoParams() {
        // when
        var launch = Launch.of("orders.brat.yaml");

        // then
        assertThat(launch.suite()).isEqualTo("orders.brat.yaml");
        assertThat(launch.environmentDirectory()).isNull();
        assertThat(launch.params()).isEmpty();
    }

    @Test
    void of_throwsForANullOrBlankSuite() {
        // then
        assertThatThrownBy(() -> Launch.of(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Launch.of(" ")).isInstanceOf(BratException.class);
    }

    @Test
    void withEnvironmentDirectory_returnsACopyWithTheDirectory() {
        // given
        var launch = Launch.of("orders.brat.yaml").withParams(Map.of("a", "1"));

        // when
        var result = launch.withEnvironmentDirectory("dev");

        // then
        assertThat(result.environmentDirectory()).isEqualTo("dev");
        assertThat(result.suite()).isEqualTo("orders.brat.yaml");
        assertThat(result.params()).containsOnly(entry("a", "1"));
        assertThat(launch.environmentDirectory()).isNull();
    }

    @Test
    void withEnvironmentDirectory_throwsForNullOrBlank() {
        // given
        var launch = Launch.of("orders.brat.yaml");

        // then
        assertThatThrownBy(() -> launch.withEnvironmentDirectory(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> launch.withEnvironmentDirectory("")).isInstanceOf(BratException.class);
    }

    @Test
    void withParams_returnsACopyWithTheParamsReplaced() {
        // given
        var launch =
                Launch.of("orders.brat.yaml").withEnvironmentDirectory("dev").withParams(Map.of("a", "1"));

        // when
        var result = launch.withParams(Map.of("b", "2"));

        // then
        assertThat(result.params()).containsOnly(entry("b", "2"));
        assertThat(result.environmentDirectory()).isEqualTo("dev");
        assertThat(launch.params()).containsOnly(entry("a", "1"));
    }

    @Test
    void withParams_copiesAndHandsOutAnUnmodifiableMap() {
        // given
        var params = new HashMap<>(Map.of("a", "1"));
        var launch = Launch.of("orders.brat.yaml").withParams(params);

        // when
        params.put("b", "2");

        // then
        assertThat(launch.params()).containsOnly(entry("a", "1"));
        assertThatThrownBy(() -> launch.params().put("c", "3")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withParams_throwsForNullParamsOrANullKeyOrValue() {
        // given
        var launch = Launch.of("orders.brat.yaml");
        var nullKey = new HashMap<String, String>();
        nullKey.put(null, "1");
        var nullValue = new HashMap<String, String>();
        nullValue.put("a", null);

        // then
        assertThatThrownBy(() -> launch.withParams(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> launch.withParams(nullKey)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> launch.withParams(nullValue)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForABlankButNotANullDirectory() {
        // then - null means no directory; blank is a mistake
        assertThat(new Launch("s.brat.yaml", null, null, Map.of()).environmentDirectory())
                .isNull();
        assertThatThrownBy(() -> new Launch("s.brat.yaml", " ", null, Map.of())).isInstanceOf(BratException.class);
    }

    // ---------- an environment by name ----------

    @Test
    void constructor_throwsForABlankButNotANullEnvironment() {
        // then - null means no environment; blank is a mistake
        assertThat(new Launch("s.brat.yaml", null, null, Map.of()).environment())
                .isNull();
        assertThatThrownBy(() -> new Launch("s.brat.yaml", null, " ", Map.of())).isInstanceOf(BratException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"envs/dev", "envs\\dev", ".", ".."})
    void constructor_rejectsAnEnvironmentThatIsAPath(String path) {
        // then - a name means a directory beside the suite; anything else is an environment directory
        assertThatThrownBy(() -> new Launch("s.brat.yaml", null, path, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(path)
                .hasMessageContaining("environment directory");
    }

    @Test
    void constructor_rejectsAnEnvironmentAndAnEnvironmentDirectoryTogether() {
        // then
        assertThatThrownBy(() -> new Launch("s.brat.yaml", "envs/dev", "dev", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("envs/dev")
                .hasMessageContaining("'dev'");
    }

    @Test
    void withEnvironment_namesTheEnvironmentInPlaceOfADirectory() {
        // given
        var launch = Launch.of("orders-api").withEnvironmentDirectory("elsewhere/dev");

        // when
        var result = launch.withEnvironment("dev");

        // then
        assertThat(result.environment()).isEqualTo("dev");
        assertThat(result.environmentDirectory()).isNull();
        assertThat(result.suite()).isEqualTo("orders-api");
    }

    @Test
    void withEnvironmentDirectory_replacesAnEnvironmentName() {
        // given
        var launch = Launch.of("orders-api").withEnvironment("dev");

        // when
        var result = launch.withEnvironmentDirectory("elsewhere/dev");

        // then
        assertThat(result.environmentDirectory()).isEqualTo("elsewhere/dev");
        assertThat(result.environment()).isNull();
    }

    @Test
    void withEnvironment_rejectsABlankNameOrAPath() {
        // given
        var launch = Launch.of("orders-api");

        // then
        assertThatThrownBy(() -> launch.withEnvironment(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> launch.withEnvironment(" ")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> launch.withEnvironment("envs/dev")).isInstanceOf(BratException.class);
    }

    @Test
    void withParams_keepsTheEnvironment() {
        // when
        var result = Launch.of("orders-api").withEnvironment("dev").withParams(Map.of("a", "1"));

        // then
        assertThat(result.environment()).isEqualTo("dev");
    }
}

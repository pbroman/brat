package dev.pbroman.brat.core.launch;

import java.util.Map;

import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreparedRunTest {

    @Test
    void constructor_throwsForANullSuiteOrEnvironment() {
        // given
        var suite = new TestSuite("s", null, null, null, null, null, null, null, null, null);
        var environment = Environment.of(Map.of(), Map.of());

        // then
        assertThatThrownBy(() -> new PreparedRun(null, environment)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new PreparedRun(suite, null)).isInstanceOf(BratException.class);
    }
}

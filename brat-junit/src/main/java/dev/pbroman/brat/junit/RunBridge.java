package dev.pbroman.brat.junit;

import java.util.stream.Stream;

import dev.pbroman.brat.core.launch.PreparedRun;
import dev.pbroman.brat.core.runner.Brat;
import org.junit.jupiter.api.DynamicNode;

/**
 * Turns one run of a suite into the dynamic tests JUnit executes, as the run happens.
 */
final class RunBridge {

    private RunBridge() {
        // static entry point only
    }

    /**
     * Returns the tests of {@code prepared}'s run, which starts when JUnit takes the first of them.
     *
     * @param brat the runner, owned from here on: closed when the run ends; never {@code null}
     * @param prepared the suite and its environment; never {@code null}
     * @return the tests
     */
    static Stream<DynamicNode> nodes(Brat brat, PreparedRun prepared) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}

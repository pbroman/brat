package dev.pbroman.brat.integration.stub;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.exception.BratException;

/**
 * A run reporter reached only through {@code META-INF/services}, proving that a plugin jar's reporter is
 * discovered, selectable by name, handed its arguments, and given a real run.
 *
 * <p>Its listener records every event, so a test can read back what the run delivered. It takes one
 * optional argument, {@code label}, and rejects any other, as a real reporter must.
 */
public final class StubRunReporter implements RunReporter {

    /** The name this reporter is selected by. */
    public static final String NAME = "stub";

    private static final Set<String> KNOWN_ARGS = Set.of("label");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RunListener create(ReporterContext context) {
        for (var key : context.args().keySet()) {
            if (!KNOWN_ARGS.contains(key)) {
                throw new BratException("Unknown argument '" + key + "' for reporter 'stub'; known: label");
            }
        }
        return new Recording(context.args().get("label"));
    }

    /** What the stub reporter creates: records the run it is given. */
    public static final class Recording implements RunListener {

        private final String label;
        private final List<RunEvent> events = new ArrayList<>();

        private Recording(String label) {
            this.label = label;
        }

        @Override
        public void on(RunEvent event) {
            events.add(event);
        }

        /**
         * The {@code label} argument this listener was created with.
         *
         * @return the label, or {@code null} if none was given
         */
        public String label() {
            return label;
        }

        /**
         * Every event received, in order.
         *
         * @return the events
         */
        public List<RunEvent> events() {
            return events;
        }
    }
}

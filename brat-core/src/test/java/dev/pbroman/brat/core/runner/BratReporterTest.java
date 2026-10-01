package dev.pbroman.brat.core.runner;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The run-reporter side of {@link Brat}: registration, selection by name, and the default reporters. */
class BratReporterTest {

    private final HttpRequestHandler handler = new HttpRequestHandler() {

        @Override
        public String name() {
            return "test";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition definition, RequestOptions options) {
            return new HttpResponse(200, Map.of(), "{}");
        }
    };

    private final Environment environment = Environment.of(Map.of("baseUrl", "http://localhost:8080"), Map.of());

    private final TestSuite suite = new TestSuite(
            "suite",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new Request(
                    "r",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new HttpRequestDefinition("${env.baseUrl}/a", "GET", null, null),
                    null,
                    null)),
            null);

    private Brat.Builder builder() {
        return Brat.builder().requestHandler(handler);
    }

    private static ClassLoader reporterPluginLoader() {
        var root = BratReporterTest.class.getClassLoader().getResource("reporter-plugin-fixture/");
        return new URLClassLoader(new URL[] {root}, BratReporterTest.class.getClassLoader());
    }

    /** A reporter recording every context it was given and every listener it created. */
    private static final class RecordingReporter implements RunReporter {

        private final String name;
        private final List<ReporterContext> contexts = new ArrayList<>();
        private final List<RecordingListener> listeners = new ArrayList<>();

        private RecordingReporter(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public RunListener create(ReporterContext context) {
            contexts.add(context);
            var listener = new RecordingListener();
            listeners.add(listener);
            return listener;
        }
    }

    private static final class RecordingListener implements RunListener {

        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public void on(RunEvent event) {
            events.add(event);
        }
    }

    /** A reporter that accepts no arguments at all, as a real one rejects keys it does not know. */
    private static final class StrictReporter implements RunReporter {

        @Override
        public String name() {
            return "strict";
        }

        @Override
        public RunListener create(ReporterContext context) {
            if (!context.args().isEmpty()) {
                throw new BratException("Unknown args: " + context.args().keySet());
            }
            return event -> {};
        }
    }

    /** A reporter that cannot work without a {@code file} argument. */
    private static final class NeedsFileReporter implements RunReporter {

        @Override
        public String name() {
            return "needs-file";
        }

        @Override
        public RunListener create(ReporterContext context) {
            if (!context.args().containsKey("file")) {
                throw new BratException("The 'needs-file' reporter requires a 'file' argument");
            }
            return event -> {};
        }
    }

    /** Declared in the reporter plugin fixture. */
    public static final class DiscoverableReporter implements RunReporter {

        @Override
        public String name() {
            return "discovered";
        }

        @Override
        public RunListener create(ReporterContext context) {
            return new DiscoveredListener();
        }
    }

    /** What {@link DiscoverableReporter} creates, so a test can tell it from a wired reporter's. */
    public static final class DiscoveredListener implements RunListener {

        @Override
        public void on(RunEvent event) {
            // records nothing; only its type matters
        }
    }

    /** Declared in the reporter plugin fixture: renders the kind {@code "discovered"}. */
    public static final class DiscoverableRendererRule implements OutcomeRendererRule {

        @Override
        public Optional<String> render(String kind, RenderTarget target) {
            return "discovered".equals(kind) ? Optional.of("from the plugin") : Optional.empty();
        }
    }

    private static final class NeverCancelled implements RunControl {

        @Override
        public void cancel() {
            // never asked to in these tests
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static OutcomeRenderer rendererSeenBy(Brat brat, RecordingReporter reporter) {
        brat.reporter(reporter.name(), Map.of());
        return reporter.contexts.getLast().renderer();
    }

    // --- reporter(name, args) ---

    @Test
    void reporter_returnsTheListenerTheNamedReporterCreates() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder().runReporter(recording).build();

        // when
        var listener = brat.reporter("rec", Map.of());

        // then
        assertThat(listener).isSameAs(recording.listeners.getLast());
    }

    @Test
    void reporter_passesTheArgsThrough() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder().runReporter(recording).build();

        // when
        brat.reporter("rec", Map.of("detail", "all"));

        // then
        assertThat(recording.contexts.getLast().args()).containsExactly(Map.entry("detail", "all"));
    }

    @Test
    void reporter_createsAFreshListenerOnEveryCall() {
        // given
        var brat = builder().runReporter(new RecordingReporter("rec")).build();

        // when
        var first = brat.reporter("rec", Map.of());
        var second = brat.reporter("rec", Map.of());

        // then
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void reporter_throwsForAnUnknownNameNamingTheRegisteredOnes() {
        // given
        var brat = builder()
                .runReporter(new RecordingReporter("rec"))
                .runReporter(new StrictReporter())
                .build();

        // when / then
        assertThatThrownBy(() -> brat.reporter("recc", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("recc")
                .hasMessageContaining("rec")
                .hasMessageContaining("strict");
    }

    @Test
    void reporter_throwsForANullNameOrArgs() {
        // given
        var brat = builder().runReporter(new RecordingReporter("rec")).build();

        // when / then
        assertThatThrownBy(() -> brat.reporter(null, Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> brat.reporter("rec", null)).isInstanceOf(BratException.class);
    }

    @Test
    void reporter_propagatesAReportersRejectionOfItsArgs() {
        // given - a typo in an argument fails before the run, not during it
        var brat = builder().runReporter(new StrictReporter()).build();

        // when / then
        assertThatThrownBy(() -> brat.reporter("strict", Map.of("detial", "all")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("detial");
    }

    @Test
    void reporter_throwsWhenTheReporterCreatesNoListener() {
        // given - a broken plugin; a null listener would otherwise fail on every event of the run
        var broken = new RunReporter() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public RunListener create(ReporterContext context) {
                return null;
            }
        };
        var brat = builder().runReporter(broken).build();

        // when / then
        assertThatThrownBy(() -> brat.reporter("broken", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("broken");
    }

    @Test
    void reporter_handsTheReporterARendererKnowingTheCoreKinds() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder().runReporter(recording).build();
        var target = new RenderTarget(Map.of("url", new InterpolationOutcome("http://x", "${env.x} → http://x")));

        // when
        var renderer = rendererSeenBy(brat, recording);

        // then
        assertThat(renderer.render("console", target)).isEqualTo("url: ${env.x} → http://x");
        assertThat(renderer.render("log", target)).contains("url=");
    }

    @Test
    void reporter_handsEveryReporterTheSameRenderer() {
        // given
        var first = new RecordingReporter("first");
        var second = new RecordingReporter("second");
        var brat = builder().runReporter(first).runReporter(second).build();

        // when
        var seenByFirst = rendererSeenBy(brat, first);
        var seenBySecond = rendererSeenBy(brat, second);

        // then
        assertThat(seenByFirst).isSameAs(seenBySecond);
    }

    // --- registration ---

    @Test
    void builder_throwsForANullReporterOrRendererRule() {
        // when / then
        assertThatThrownBy(() -> Brat.builder().runReporter(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().outcomeRendererRule(null)).isInstanceOf(BratException.class);
    }

    @Test
    void builder_letsALaterReporterReplaceAnEarlierOneOfTheSameName() {
        // given
        var earlier = new RecordingReporter("rec");
        var later = new RecordingReporter("rec");
        var brat = builder().runReporter(earlier).runReporter(later).build();

        // when
        brat.reporter("rec", Map.of());

        // then
        assertThat(later.listeners).hasSize(1);
        assertThat(earlier.listeners).isEmpty();
    }

    @Test
    void builder_addedOutcomeRendererRuleIsReachedThroughTheRenderer() {
        // given
        var recording = new RecordingReporter("rec");
        OutcomeRendererRule json = (kind, target) -> "json".equals(kind) ? Optional.of("{}") : Optional.empty();
        var brat = builder().runReporter(recording).outcomeRendererRule(json).build();

        // when
        var renderer = rendererSeenBy(brat, recording);

        // then
        assertThat(renderer.render("json", new RenderTarget(Map.of()))).isEqualTo("{}");
    }

    @Test
    void build_discoversRunReporters() {
        // given
        var brat = builder().classLoader(reporterPluginLoader()).build();

        // when
        var listener = brat.reporter("discovered", Map.of());

        // then
        assertThat(listener).isInstanceOf(DiscoveredListener.class);
    }

    @Test
    void build_letsADiscoveredReporterReplaceAWiredOneOfTheSameName() {
        // given - core, then the builder, then the classpath: the plugin is the most specific
        var wired = new RecordingReporter("discovered");
        var brat =
                builder().runReporter(wired).classLoader(reporterPluginLoader()).build();

        // when
        var listener = brat.reporter("discovered", Map.of());

        // then
        assertThat(listener).isInstanceOf(DiscoveredListener.class);
        assertThat(wired.listeners).isEmpty();
    }

    @Test
    void build_discoversOutcomeRendererRules() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder()
                .runReporter(recording)
                .classLoader(reporterPluginLoader())
                .build();

        // when
        var renderer = rendererSeenBy(brat, recording);

        // then
        assertThat(renderer.render("discovered", new RenderTarget(Map.of()))).isEqualTo("from the plugin");
    }

    // --- default reporters ---

    @Test
    void build_registersTheConsoleReporter() {
        // when
        var listener = builder().build().reporter("console", Map.of());

        // then
        assertThat(listener).isNotNull();
    }

    @Test
    void run_reportsToTheConsoleUnlessDefaultsAreSet() {
        // given - a reporter registered as "console" replaces core's, so the default reaches it
        var console = new RecordingReporter("console");
        var brat = builder().runReporter(console).build();

        // when
        var result = brat.run(suite, environment);

        // then
        assertThat(console.listeners.getLast().events).last().isEqualTo(new RunEvent.RunFinished(result));
    }

    @Test
    void builder_throwsForInvalidDefaultReporters() {
        // when / then
        assertThatThrownBy(() -> Brat.builder().defaultReporters(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultReporters(Arrays.asList("rec", null)))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> Brat.builder().defaultReporters(List.of(" "))).isInstanceOf(BratException.class);
    }

    @Test
    void build_failsWhenADefaultReporterIsNotRegistered() {
        // given
        var builder = builder().runReporter(new RecordingReporter("rec")).defaultReporters(List.of("recc"));

        // when / then - a typo must not become an accidentally silent run
        assertThatThrownBy(builder::build)
                .isInstanceOf(BratException.class)
                .hasMessageContaining("recc")
                .hasMessageContaining("rec");
    }

    @Test
    void build_failsWhenADefaultReporterRejectsEmptyArgs() {
        // given - it would otherwise build fine and make every run(suite, env) throw
        var builder = builder().runReporter(new NeedsFileReporter()).defaultReporters(List.of("needs-file"));

        // when / then
        assertThatThrownBy(builder::build).isInstanceOf(BratException.class).hasMessageContaining("file");
    }

    @Test
    void build_failsWhenADefaultReporterCreatesNoListener() {
        // given - a broken plugin as a default; every run(suite, env) would otherwise throw
        var broken = new RunReporter() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public RunListener create(ReporterContext context) {
                return null;
            }
        };
        var builder = builder().runReporter(broken).defaultReporters(List.of("broken"));

        // when / then
        assertThatThrownBy(builder::build).isInstanceOf(BratException.class).hasMessageContaining("broken");
    }

    @Test
    void run_reportsThroughEachDefaultReporterInOrder() {
        // given
        var first = new RecordingReporter("first");
        var second = new RecordingReporter("second");
        var brat = builder()
                .runReporter(first)
                .runReporter(second)
                .defaultReporters(List.of("second", "first"))
                .build();

        // when
        var result = brat.run(suite, environment);

        // then - each default got a whole run, with empty args
        for (var reporter : List.of(first, second)) {
            var events = reporter.listeners.getLast().events;
            assertThat(events.getFirst()).isInstanceOf(RunEvent.RunStarted.class);
            assertThat(events.getLast()).isEqualTo(new RunEvent.RunFinished(result));
            assertThat(reporter.contexts.getLast().args()).isEmpty();
        }
    }

    @Test
    void run_createsFreshDefaultListenersForEveryRun() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder()
                .runReporter(recording)
                .defaultReporters(List.of("rec"))
                .build();
        var createdAtBuild = recording.listeners.size();

        // when
        brat.run(suite, environment);
        brat.run(suite, environment);

        // then
        assertThat(recording.listeners).hasSize(createdAtBuild + 2).doesNotHaveDuplicates();
        assertThat(recording.listeners.getLast().events)
                .filteredOn(RunEvent.RunFinished.class::isInstance)
                .hasSize(1);
    }

    @Test
    void run_isSilentWithNoDefaultReporters() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder().runReporter(recording).defaultReporters(List.of()).build();

        // when
        brat.run(suite, environment);

        // then - registered, but nothing selected it
        assertThat(recording.listeners).isEmpty();
    }

    @Test
    void run_withExplicitListenersDoesNotAddTheDefaults() {
        // given
        var recording = new RecordingReporter("rec");
        var brat = builder()
                .runReporter(recording)
                .defaultReporters(List.of("rec"))
                .build();
        var createdAtBuild = recording.listeners.size();
        var mine = new RecordingListener();

        // when
        brat.run(suite, environment, List.of(mine), new NeverCancelled());

        // then
        assertThat(recording.listeners).hasSize(createdAtBuild);
        assertThat(mine.events).isNotEmpty();
    }

    @Test
    void run_deliversNoEventWhenItFailsAtLaunch() {
        // given - a missing body file fails before RunStarted
        var definition =
                new HttpRequestDefinition("http://url", "POST", Map.of("file", "file:/no/such/body.json"), null);
        var request = new Request("create", null, null, null, null, null, null, definition, null, null);
        var failing = new TestSuite("suite", null, null, null, null, null, null, null, List.of(request), null);
        var mine = new RecordingListener();

        // when
        assertThatThrownBy(() -> builder().build().run(failing, environment, List.of(mine), new NeverCancelled()))
                .isInstanceOf(BratException.class);

        // then - so a listener acquiring on RunStarted never holds anything
        assertThat(mine.events).isEmpty();
    }
}

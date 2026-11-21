package dev.pbroman.brat.junit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.rendering.OutcomeRendererRuleDispatcher;
import dev.pbroman.brat.core.rendering.RequestRenderer;
import dev.pbroman.brat.core.rendering.rules.ConsoleOutcomeRendererRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPrinterTest {

    private static final String SECRET = "s3cr3t";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final OutcomeRendererRuleDispatcher renderer =
            new OutcomeRendererRuleDispatcher(List.of(new ConsoleOutcomeRendererRule()));
    private final ValueMask mask = new ValueMask();

    private List<String> printed() {
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private static Output output(Map<String, String> show) {
        return new Output(false, ReportDetail.of(show));
    }

    private static RequestResult result(AssertionResult... assertions) {
        var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Content-Type", List.of("application/json"));
        var vars = new LinkedHashMap<String, Object>();
        vars.put("statusCode", 200);
        vars.put("headers", headers);
        vars.put("body", "{\"token\":\"" + SECRET + "\"}");
        vars.put("json", "{\"token\":\"" + SECRET + "\"}");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("http://h/x", "${env.baseUrl}/x → http://h/x"));
        return new RequestResult(
                new RequestCoordinates("suite/read", null, "read", Phase.MAIN, 1),
                new HttpRequestDefinition("http://h/x", "GET", null, Map.of(), null, outcomes),
                null,
                new RequestStatus.Completed(vars, 1, 12),
                12,
                0,
                new ResponseActionsResult(List.of(assertions), List.of()));
    }

    private static AssertionResult failing() {
        var condition = new Condition("isEqualTo", "200", "201", null, new LinkedHashMap<>());
        return new AssertionResult(condition, null, false, AssertionSeverity.FAIL);
    }

    @Test
    void request_printsJustTheLineOfAPassedRequestByDefault() {
        // given
        var passed = result();

        // when
        TestPrinter.request(out, passed, output(Map.of()), mask, renderer);

        // then
        assertThat(printed()).containsExactly(RequestRenderer.headline(passed, mask, false));
    }

    @Test
    void request_printsWhatTheDetailShowsBeneathAPassedRequestIndented() {
        // given
        var passed = result();

        // when
        TestPrinter.request(out, passed, output(Map.of("response", "all")), mask, renderer);

        // then
        var lines = printed();
        assertThat(lines.getFirst()).isEqualTo(RequestRenderer.headline(passed, mask, false));
        assertThat(lines.subList(1, lines.size()))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).startsWith(RequestRenderer.DETAIL_INDENT))
                .anySatisfy(line -> assertThat(line).isEqualTo(RequestRenderer.DETAIL_INDENT + "response: 200"));
    }

    @Test
    void request_printsOnlyTheLineOfAFailedRequestWhoseDetailIsInItsMessage() {
        // given
        var failed = result(failing());

        // when
        TestPrinter.request(out, failed, output(Map.of()), mask, renderer);

        // then
        assertThat(printed()).containsExactly(RequestRenderer.headline(failed, mask, false));
        assertThat(printed().getFirst()).startsWith("FAIL");
    }

    @Test
    void request_masksWhatItPrints() {
        // given
        mask.addSecret(SECRET);

        // when
        TestPrinter.request(out, result(), output(Map.of("response", "all")), mask, renderer);

        // then
        assertThat(String.join("\n", printed())).doesNotContain(SECRET).contains("***");
    }

    @Test
    void request_printsNothingWhenQuiet() {
        // when
        TestPrinter.request(out, result(failing()), new Output(true, ReportDetail.of(Map.of())), mask, renderer);

        // then
        assertThat(printed()).isEmpty();
    }

    @Test
    void end_printsTheVerdictTheNameAndTheReasonMasked() {
        // given
        mask.addSecret(SECRET);

        // when
        TestPrinter.end(out, "ABORTED", "checkout", "login refused " + SECRET, output(Map.of()), mask);

        // then
        assertThat(printed()).containsExactly("ABORTED checkout — login refused ***");
    }

    @Test
    void end_printsNothingWhenQuiet() {
        // when
        TestPrinter.end(out, "SKIPPED", "later", "not yet", new Output(true, ReportDetail.of(Map.of())), mask);

        // then
        assertThat(printed()).isEmpty();
    }
}

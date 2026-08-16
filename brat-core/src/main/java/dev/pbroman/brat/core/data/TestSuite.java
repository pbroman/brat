package dev.pbroman.brat.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A suite of requests, and the subSuites beneath it — the whole authored document, and every node of
 * the tree inside it.
 * <p>
 * A structural container, deliberately <em>not</em> a {@link ConfigData}, for the same reason as
 * {@link Request}: what it holds that interpolates is other types, interpolated per request rather
 * than per suite.
 * <p>
 * Several fields are bound here and read by nothing until later phases — {@code auth} until 8d,
 * {@code requestHandlers} until 8a-2, {@code skipCondition} and {@code phase} until 8b. They are
 * defined now because the loader rejects unknown keys, so an author writing a legal key must find a
 * field waiting for it; later phases add <em>readers</em>, not fields.
 *
 * @param name names the node in reports and forms a segment of every path beneath it. The loader
 *        requires it, requires it to be unique among its siblings, and rejects a {@code /} in it —
 *        this type enforces none of that, so a directly-constructed instance may hold anything
 * @param description optional prose, shown in reports and read by nothing
 * @param constants fixed values read as {@code ${constants.x}}; never {@code null}
 * @param setVars values computed when this suite starts, read as {@code ${vars.x}}; never
 *        {@code null}
 * @param auth credentials for requests beneath this suite, or {@code null}. Replaced wholesale by a
 *        subSuite that declares its own, which is what lets {@code type: none} cancel an inherited
 *        login
 * @param timeout the default request timeout in milliseconds, in text form, or {@code null} for
 *        {@link dev.pbroman.brat.core.util.Constants#DEFAULT_TIMEOUT_MS}. Named for what it is rather
 *        than prefixed: a request's own {@code timeout} is the same word one level down, exactly as
 *        {@code auth} is
 * @param skipCondition skip this suite and everything under it when it holds, or {@code null}
 * @param phase when this suite runs relative to its siblings; never {@code null}, defaulting to
 *        {@link Phase#MAIN}
 * @param requestHandlers which handler executes a request, keyed by protocol; never {@code null}.
 *        Merges per key rather than replacing wholesale
 * @param requests this suite's own requests, in declaration order; never {@code null}
 * @param subSuites nested suites, in declaration order; never {@code null}
 */
public record TestSuite(
        String name,
        String description,
        Map<String, Object> constants,
        Map<String, String> setVars,
        Auth auth,
        String timeout,
        Condition skipCondition,
        Phase phase,
        Map<String, String> requestHandlers,
        List<Request> requests,
        List<TestSuite> subSuites) {

    /**
     * Defaults every collection to empty and {@code phase} to {@link Phase#MAIN}, and copies the
     * mutable arguments.
     * <p>
     * No collection is ever {@code null} on a constructed instance, so the walk iterates without
     * guarding. Copies tolerate {@code null} entries rather than rejecting them — authored YAML is
     * where nulls come from, and rejecting one here would throw the wrong exception type from the
     * wrong place.
     */
    public TestSuite {
        constants = constants == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(constants));
        setVars = setVars == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(setVars));
        phase = phase == null ? Phase.MAIN : phase;
        requestHandlers =
                requestHandlers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestHandlers));
        requests = requests == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(requests));
        subSuites = subSuites == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(subSuites));
    }
}

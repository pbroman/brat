package dev.pbroman.brat.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.util.NamespaceUtils;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;

/**
 * A suite of requests, and the subSuites beneath it — the whole authored document, and every node of
 * the tree inside it.
 * <p>
 * A structural container, deliberately <em>not</em> a {@link ConfigData}, for the same reason as
 * {@link Request}: what it holds that interpolates is other types, interpolated per request rather
 * than per suite.
 * <p>
 * {@code auth} binds here and is read by nothing yet. It is defined because the loader rejects unknown
 * keys, so an author writing a legal key must find a field waiting for it; what is still missing is a
 * <em>reader</em>, not a field.
 *
 * @param name names the node in reports and forms a segment of every path beneath it. The loader
 *        requires it, requires it to be unique among its siblings, and rejects a {@code /} in it —
 *        this type enforces none of that, so a directly-constructed instance may hold anything
 * @param description optional prose, shown in reports and read by nothing
 * @param constants fixed values read as {@code ${constants.x}}; never {@code null}. Flat: a value
 *        written nested is held under its dotted key, so {@code db: {host: x}} is read as
 *        {@code ${constants.db.host}}. Literal: no value holds a {@code ${...}} token
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
        Map<String, String> requestHandlers,
        List<Request> requests,
        List<TestSuite> subSuites) {

    /**
     * Defaults every collection to empty, copies the mutable arguments, and flattens {@code constants}
     * to dotted keys.
     * <p>
     * No collection is ever {@code null} on a constructed instance, so the walk iterates without
     * guarding. Copies tolerate {@code null} entries rather than rejecting them — authored YAML is
     * where nulls come from, and rejecting one here would throw the wrong exception type from the
     * wrong place.
     *
     * @throws dev.pbroman.brat.core.exception.BratException if {@code constants}, once flattened, would
     *         hold one key twice, or if any of its values holds a {@code ${...}} token — each naming the
     *         key as {@code constants.<key>} and never the value
     */
    public TestSuite {
        constants = constants == null ? Map.of() : NamespaceUtils.flattenLiteral(constants, CONSTANTS);
        setVars = setVars == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(setVars));
        requestHandlers =
                requestHandlers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestHandlers));
        requests = requests == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(requests));
        subSuites = subSuites == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(subSuites));
    }
}

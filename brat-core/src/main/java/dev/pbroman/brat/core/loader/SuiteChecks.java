package dev.pbroman.brat.core.loader;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.PATH_DELIMITER;
import static dev.pbroman.brat.core.util.Constants.TOKEN_PREFIX;

/**
 * The rules a bound suite must satisfy that binding itself cannot express.
 * <p>
 * Jackson can reject a key it does not know and a value of the wrong shape. It cannot say that two
 * siblings share a name, that an id is claimed twice, that {@code constants} belong only to the
 * root, or that an auth type is one something can apply — those are properties of the document as a
 * whole, or of what the runner knows, and this is where they live. Split out of
 * {@code SuiteLoader} because the rule set grows and is what a maintainer comes back to, while
 * composing and binding do not change.
 * <p>
 * ⚠ <strong>Not a validation pass</strong>, which this project deliberately does not have. The
 * difference is real rather than verbal: a validation pass inspects data it could otherwise have used,
 * while these reject data that cannot be coherently represented at all — two nodes sharing one address
 * are indistinguishable at every later point of use, so there is no later point at which to fail.
 */
final class SuiteChecks {

    private SuiteChecks() {
        // utility class
    }

    /**
     * Applies every rule to a bound suite, throwing on the first that fails.
     *
     * @param suite the bound root suite
     * @param document the converted document, for positions
     * @param origin what to call the document in an error
     * @param authTypes the auth types a token-free {@code type} may name
     * @throws BratException on the first rule a node breaks: a missing
     *         name, a {@code /} inside one, two siblings sharing one, two requests sharing an
     *         {@code id}, {@code constants} below the root, a {@code repeatUntil} with no
     *         {@code condition}, or an {@code auth} breaking {@link #checkAuth}'s rules
     */
    static void check(TestSuite suite, YamlDocument document, String origin, Set<String> authTypes) {
        var ids = new HashSet<String>();
        checkSuite(suite, "", true, ids, document, origin, authTypes);
    }

    /**
     * Checks one suite and recurses, collecting authored ids as it goes.
     *
     * @param suite the suite to check
     * @param pointer the suite's JSON Pointer
     * @param isRoot whether this is the document's root suite
     * @param ids the authored ids seen so far, added to
     * @param document the converted document
     * @param origin what to call the document
     * @param authTypes the auth types a token-free {@code type} may name
     */
    private static void checkSuite(
            TestSuite suite,
            String pointer,
            boolean isRoot,
            Set<String> ids,
            YamlDocument document,
            String origin,
            Set<String> authTypes) {
        checkName(suite.name(), pointer, document, origin);
        checkAuth(suite.auth(), pointer + "/auth", authTypes, document, origin);
        if (!isRoot && !suite.constants().isEmpty()) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/constants",
                    "Only the root suite may declare 'constants'. A nested one would stay visible to "
                            + "every later sibling, which is not what the word promises");
        }

        var siblings = new LinkedHashSet<String>();
        var requests = suite.requests();
        for (var i = 0; i < requests.size(); i++) {
            var request = requests.get(i);
            var requestPointer = pointer + "/requests/" + i;
            checkName(request.name(), requestPointer, document, origin);
            claimSibling(siblings, request.name(), requestPointer, document, origin);
            claimId(ids, request, requestPointer, document, origin);
            checkRequired(request, requestPointer, document, origin);
            checkAuth(request.auth(), requestPointer + "/auth", authTypes, document, origin);
        }

        var subSuites = suite.subSuites();
        for (var i = 0; i < subSuites.size(); i++) {
            var subPointer = pointer + "/subSuites/" + i;
            claimSibling(siblings, subSuites.get(i).name(), subPointer, document, origin);
            checkSuite(subSuites.get(i), subPointer, false, ids, document, origin, authTypes);
        }
    }

    /**
     * Requires the nested values a request cannot run without.
     * <p>
     * The interpolator rejects a conditionless {@code repeatUntil} too, but only here is there a
     * position to report it against.
     *
     * @param request the request to check
     * @param pointer the request's JSON Pointer
     * @param document the converted document
     * @param origin what to call the document
     */
    private static void checkRequired(Request request, String pointer, YamlDocument document, String origin) {
        var flowControl = request.flowControl();
        if (flowControl != null
                && flowControl.getRepeatUntil() != null
                && flowControl.getRepeatUntil().getCondition() == null) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/flowControl/repeatUntil",
                    "A 'repeatUntil' needs a 'condition'. Repeating until nothing is not a shorter way "
                            + "of writing 'repeat N times'");
        }
    }

    /**
     * Requires an {@code auth} block's type to be one something can apply.
     * <p>
     * Only a <strong>token-free</strong> type is checked, meaning one in which <code>${</code> does not
     * occur: that is the only kind whose value is known before the run. A type holding a token,
     * wholly or in part, is left for execution, where a type nothing is registered for fails that one
     * request.
     *
     * @param auth the node's auth block, or {@code null} where it declares none, which passes
     * @param pointer the auth block's JSON Pointer
     * @param authTypes the auth types a token-free type may name, matched exactly — {@code Bearer} is
     *        not {@code bearer}
     * @param document the converted document
     * @param origin what to call the document
     * @throws BratException if the block has no {@code type}, or a blank
     *         one, saying an {@code auth} block needs one, at the block's position; or if a token-free
     *         type is not in {@code authTypes}, at the type's position, naming the type and every type
     *         in {@code authTypes}, sorted
     */
    private static void checkAuth(
            Auth auth, String pointer, Set<String> authTypes, YamlDocument document, String origin) {
        if (auth == null) {
            return;
        }
        var type = auth.getType();
        if (StringUtils.isBlank(type)) {
            throw LoaderErrors.at(origin, document, pointer, "An auth must have a non-blank type");
        }
        if (!type.contains(TOKEN_PREFIX) && !authTypes.contains(type)) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/type",
                    String.format(
                            "The auth type '%s' is unknown. Known types: %s",
                            type, String.join(", ", authTypes.stream().sorted().toList())));
        }
    }

    /**
     * Requires a usable name.
     *
     * @param name the node's name
     * @param pointer the node's JSON Pointer
     * @param document the converted document
     * @param origin what to call the document
     */
    private static void checkName(String name, String pointer, YamlDocument document, String origin) {
        if (StringUtils.isBlank(name)) {
            throw LoaderErrors.at(origin, document, pointer, "Every suite and request needs a 'name'");
        }
        if (name.contains(PATH_DELIMITER)) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/name",
                    "A name may not contain '" + PATH_DELIMITER + "', which separates the segments of a path");
        }
    }

    /**
     * Claims a name among its siblings — requests and subSuites share one namespace, since both
     * contribute a path segment.
     *
     * @param siblings the names claimed so far under this parent
     * @param name the name to claim
     * @param pointer the node's JSON Pointer
     * @param document the converted document
     * @param origin what to call the document
     */
    private static void claimSibling(
            Set<String> siblings, String name, String pointer, YamlDocument document, String origin) {
        if (!siblings.add(name)) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/name",
                    "Two siblings are named '" + name + "'. They would share one address");
        }
    }

    /**
     * Claims a request's authored id across the whole document.
     *
     * @param ids the ids claimed so far
     * @param request the request
     * @param pointer the request's JSON Pointer
     * @param document the converted document
     * @param origin what to call the document
     */
    private static void claimId(
            Set<String> ids, Request request, String pointer, YamlDocument document, String origin) {
        if (request.id() != null && !ids.add(request.id())) {
            throw LoaderErrors.at(
                    origin,
                    document,
                    pointer + "/id",
                    "Two requests declare the id '" + request.id() + "'. An id correlates one request "
                            + "across runs, so it must name one");
        }
    }
}

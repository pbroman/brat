package dev.pbroman.brat.core.api.reporting;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * What a report shows under each request: which requests show the request as sent, which show the
 * response, which assertions are shown, and whether values that only look sensitive are masked.
 * <p>
 * The one place that answers "is this shown", so that every reporter using it answers alike. A
 * reporter reads it from its arguments with {@link #of(Map)}:
 *
 * <table>
 *   <caption>The arguments</caption>
 *   <tr><th>key</th><th>values</th><th>default</th></tr>
 *   <tr><td>{@code response}</td><td>{@code failures}, {@code all}, {@code none}</td><td>{@code failures}</td></tr>
 *   <tr><td>{@code request}</td><td>{@code failures}, {@code all}, {@code none}</td><td>{@code failures}</td></tr>
 *   <tr><td>{@code assertions}</td><td>{@code failures}, {@code all}</td><td>{@code failures}</td></tr>
 *   <tr><td>{@code secrets}</td><td>{@code mask}, {@code show}</td><td>{@code mask}</td></tr>
 * </table>
 *
 * {@code secrets: show} lifts only the masks a {@link ValueMask} applies to values that look
 * sensitive; a value from a secrets provider stays masked whatever it says.
 *
 * @param response for which requests the response is shown; never {@code null}
 * @param request for which requests the request as sent is shown; never {@code null}
 * @param assertions which assertions are shown; never {@code null}, and never {@link Show#NONE}, since a
 *        failed assertion is always shown
 * @param showSecrets whether values that only look sensitive are shown rather than masked
 */
public record ReportDetail(Show response, Show request, Show assertions, boolean showSecrets) {

    /** The argument keys {@link #of(Map)} reads, in the order they are documented. */
    public static final List<String> KEYS = List.of("response", "request", "assertions", "secrets");

    private static final String SECRETS_MASK = "mask";
    private static final String SECRETS_SHOW = "show";

    /**
     * Validates the components.
     *
     * @param response for which requests the response is shown
     * @param request for which requests the request as sent is shown
     * @param assertions which assertions are shown
     * @param showSecrets whether values that only look sensitive are shown
     * @throws BratException if {@code response}, {@code request} or {@code assertions} is {@code null},
     *         or if {@code assertions} is {@link Show#NONE}
     */
    public ReportDetail {
        nonNull(response, "The response detail must not be null");
        nonNull(request, "The request detail must not be null");
        nonNull(assertions, "The assertions detail must not be null");
        if (assertions == Show.NONE) {
            throw new BratException("The assertions detail cannot be none: a failed assertion is always shown");
        }
    }

    /**
     * Reads the detail from a reporter's arguments.
     * <p>
     * Only the keys in {@link #KEYS} are read; any other key is left alone, since a reporter may take
     * arguments of its own. Rejecting keys it does not know is the reporter's, which knows all of them.
     * An absent key, or one holding {@code null}, takes its default: {@code failures} for
     * {@code response}, {@code request} and {@code assertions}, {@code mask} for {@code secrets}. Values
     * are matched exactly, lower case.
     *
     * @param args a reporter's arguments; must not be {@code null}
     * @return the detail they describe; never {@code null}
     * @throws BratException if {@code args} is {@code null}; if {@code response} or {@code request} holds
     *         anything but {@code failures}, {@code all} or {@code none}; if {@code assertions} holds
     *         anything but {@code failures} or {@code all}; or if {@code secrets} holds anything but
     *         {@code mask} or {@code show}. The message names the key, the value and the values it takes
     */
    public static ReportDetail of(Map<String, String> args) {
        nonNull(args, "The reporter args must not be null");
        return new ReportDetail(
                show(args, "response", List.of(Show.values())),
                show(args, "request", List.of(Show.values())),
                show(args, "assertions", List.of(Show.FAILURES, Show.ALL)),
                showSecrets(args));
    }

    /**
     * Whether the request as sent is shown under {@code result}.
     * <p>
     * Never for a skipped request, which sent nothing, and never for {@link Show#NONE}. Otherwise always
     * for {@link Show#ALL}, and for {@link Show#FAILURES} when the request {@linkplain RequestResult#failed()
     * failed} — errored, gave up, failed a capture, or failed an assertion of severity {@code FAIL}.
     * Whether there is anything to print — a definition that was never interpolated has nothing — is
     * not decided here.
     *
     * @param result the request's result; must not be {@code null}
     * @return whether its request as sent is shown
     * @throws BratException if {@code result} is {@code null}
     */
    public boolean showsRequest(RequestResult result) {
        nonNull(result, "The request result must not be null");
        if (request == Show.NONE || result.status() instanceof RequestStatus.Skipped) {
            return false;
        }
        return request == Show.ALL || result.failed();
    }

    /**
     * Whether the response is shown under {@code result}.
     * <p>
     * Never for a request that carries no response — see {@link RequestResult#responseVars()}, so never
     * for an errored or a skipped one — and never for {@link Show#NONE}. Otherwise always for
     * {@link Show#ALL}, and for {@link Show#FAILURES} when the request
     * {@linkplain RequestResult#failed() failed}.
     *
     * @param result the request's result; must not be {@code null}
     * @return whether its response is shown
     * @throws BratException if {@code result} is {@code null}
     */
    public boolean showsResponse(RequestResult result) {
        nonNull(result, "The request result must not be null");
        // An errored or skipped request carries no response, so this check covers both.
        if (response == Show.NONE || result.responseVars().isEmpty()) {
            return false;
        }
        return response == Show.ALL || result.failed();
    }

    /**
     * Whether {@code assertion} is shown.
     * <p>
     * Always for {@link Show#ALL}. For {@link Show#FAILURES}, whenever it did not pass, at either
     * severity — so a failed {@code WARN} assertion is shown under a request that passed.
     *
     * @param assertion the assertion's result; must not be {@code null}
     * @return whether it is shown
     * @throws BratException if {@code assertion} is {@code null}
     */
    public boolean showsAssertion(AssertionResult assertion) {
        nonNull(assertion, "The assertion result must not be null");
        return assertions == Show.ALL || (assertions == Show.FAILURES && !assertion.passed());
    }

    private static Show show(Map<String, String> args, String key, List<Show> legal) {
        var value = args.get(key);
        if (value == null) {
            return Show.FAILURES;
        }
        for (var show : legal) {
            if (show.name().toLowerCase(Locale.ROOT).equals(value)) {
                return show;
            }
        }
        throw unknownValue(
                key,
                value,
                legal.stream().map(show -> show.name().toLowerCase(Locale.ROOT)).toList());
    }

    private static boolean showSecrets(Map<String, String> args) {
        var value = args.get("secrets");
        if (value == null || SECRETS_MASK.equals(value)) {
            return false;
        }
        if (SECRETS_SHOW.equals(value)) {
            return true;
        }
        throw unknownValue("secrets", value, List.of(SECRETS_MASK, SECRETS_SHOW));
    }

    private static BratException unknownValue(String key, String value, List<String> legal) {
        return new BratException(
                String.format("Unknown value '%s' for the report detail '%s'; known: %s", value, key, legal));
    }
}

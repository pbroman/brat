package dev.pbroman.brat.core.auth;

import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.ArgsUtils.rejectUnknownArgs;
import static dev.pbroman.brat.core.util.ArgsUtils.requiredNonBlankArg;
import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;

/**
 * The {@code apikey} auth type: an API key, sent either in the {@code Authorization} header with the
 * {@code ApiKey} scheme or raw in a header the suite names.
 * <p>
 * The first form is the one OpenSearch and Elasticsearch read; the second, {@code x-api-key} and the
 * like, is what AWS API Gateway and many SaaS APIs read.
 */
public final class ApiKeyAuthHandler implements AuthHandler {

    /** The type this handler is selected by. */
    public static final String TYPE = "apikey";

    private static final String SUBJECT = "the '" + TYPE + "' auth";
    private static final String KEY = "key";
    private static final String HEADER = "header";
    private static final String API_KEY = "ApiKey ";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Returns the key in one header: {@code Authorization: ApiKey <key>} when no {@code header} is
     * given, otherwise {@code <header>: <key>}, the key unaltered.
     *
     * @param auth the auth block, whose {@code args} are {@code key}, required, and {@code header},
     *        optional; never {@code null}
     * @param request the request; never {@code null}, and not read
     * @return a map holding exactly one header
     * @throws BratException if {@code auth.getArgs()} holds a key other than {@code key} and {@code header}; if
     *         {@code key} is absent or blank; or if {@code header} is given but blank. Each message names the argument
     *         and never quotes the key
     */
    @Override
    public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
        rejectUnknownArgs(auth.getArgs(), SUBJECT, KEY, HEADER);
        var key = requiredNonBlankArg(auth.getArgs(), SUBJECT, KEY);
        return auth.getArgs().containsKey(HEADER)
                ? Map.of(requiredNonBlankArg(auth.getArgs(), SUBJECT, HEADER), key)
                : Map.of(AUTHORIZATION, API_KEY + key);
    }
}

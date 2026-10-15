package dev.pbroman.brat.core.auth;

import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.util.ArgsUtils.rejectUnknownArgs;
import static dev.pbroman.brat.core.api.util.ArgsUtils.requiredNonBlankArg;
import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;

/**
 * The {@code bearer} auth type: a bearer token, such as one a login request captured.
 */
public final class BearerAuthHandler implements AuthHandler {

    /** The type this handler is selected by. */
    public static final String TYPE = "bearer";

    private static final String SUBJECT = "the '" + TYPE + "' auth";
    private static final String TOKEN = "token";
    private static final String BEARER = "Bearer ";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Returns {@code Authorization: Bearer <token>}.
     *
     * @param auth the auth block, whose one argument is {@code token}, required; never {@code null}
     * @param request the request; never {@code null}, and not read
     * @return a map holding exactly the {@code Authorization} header
     * @throws BratException if {@code auth.getArgs()} holds a key other than {@code token}, or if {@code token} is
     *         absent or blank — the usual sign of a login whose capture has not run. The message names the argument
     *         and never quotes its value
     */
    @Override
    public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
        rejectUnknownArgs(auth.getArgs(), SUBJECT, TOKEN);
        return Map.of(AUTHORIZATION, BEARER + requiredNonBlankArg(auth.getArgs(), SUBJECT, TOKEN));
    }
}

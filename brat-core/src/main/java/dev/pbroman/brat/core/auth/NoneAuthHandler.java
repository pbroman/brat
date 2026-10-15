package dev.pbroman.brat.core.auth;

import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.util.ArgsUtils.rejectUnknownArgs;

/**
 * The {@code none} auth type: sends no credentials.
 * <p>
 * Declared explicitly, it cancels an auth block inherited from an enclosing suite. It takes no
 * arguments.
 */
public final class NoneAuthHandler implements AuthHandler {

    /** The type this handler is selected by. */
    public static final String TYPE = "none";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Returns no headers.
     *
     * @param auth the auth block; never {@code null}
     * @param request the request; never {@code null}, and not read
     * @return an empty map
     * @throws BratException if {@code auth.getArgs()} holds any argument, naming it
     */
    @Override
    public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
        rejectUnknownArgs(auth.getArgs(), "the '" + TYPE + "' auth");
        return Map.of();
    }
}

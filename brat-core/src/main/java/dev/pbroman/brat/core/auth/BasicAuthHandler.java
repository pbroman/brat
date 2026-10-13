package dev.pbroman.brat.core.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.ArgsUtils.rejectUnknownArgs;
import static dev.pbroman.brat.core.util.ArgsUtils.requiredNonBlankArg;
import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;

/**
 * The {@code basic} auth type: HTTP Basic authentication, from a {@code username} and a
 * {@code password}.
 */
public final class BasicAuthHandler implements AuthHandler {

    /** The type this handler is selected by. */
    public static final String TYPE = "basic";

    private static final String SUBJECT = "the '" + TYPE + "' auth";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String BASIC = "Basic ";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Returns {@code Authorization: Basic <credentials>}, where the credentials are
     * {@code username:password} encoded as UTF-8 and then Base64.
     *
     * @param auth the auth block, whose {@code args} are {@code username} and {@code password}, both
     *        required; never {@code null}
     * @param request the request; never {@code null}, and not read
     * @return a map holding exactly the {@code Authorization} header
     * @throws dev.pbroman.brat.core.exception.BratException if {@code auth.getArgs()} holds a key
     *         other than {@code username} and {@code password}; if either is absent or blank; or if
     *         {@code username} contains a {@code :}, which Basic authentication cannot encode. Each
     *         message names the argument and never quotes its value
     */
    @Override
    public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
        rejectUnknownArgs(auth.getArgs(), SUBJECT, USERNAME, PASSWORD);
        var username = requiredNonBlankArg(auth.getArgs(), SUBJECT, USERNAME);
        var password = requiredNonBlankArg(auth.getArgs(), SUBJECT, PASSWORD);
        if (username.contains(":")) {
            throw new BratException(
                    String.format("The '%s' argument contains ':', which %s cannot encode", USERNAME, SUBJECT));
        }
        var raw = username + ":" + password;
        var encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return Map.of(AUTHORIZATION, BASIC + encoded);
    }
}

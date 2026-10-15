package dev.pbroman.brat.integration.stub;

import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.util.ArgsUtils;

/**
 * An auth type core has never heard of, {@code stub-signature}: it signs a request by sending its
 * {@code secret} argument, reversed, in an {@code X-Stub-Signature} header.
 *
 * <p>The signature is nonsense on purpose. What it shows is the whole path a real plugin type such as
 * a request signer walks: discovered from {@code META-INF/services}, accepted by the loader because
 * the runner registered it, handed the interpolated arguments, and its header applied by core.
 */
public final class StubAuthHandler implements AuthHandler {

    /** The auth type this handler serves. */
    public static final String TYPE = "stub-signature";

    /** The header the signature travels in. */
    public static final String HEADER = "X-Stub-Signature";

    private static final String SUBJECT = "the '" + TYPE + "' auth";
    private static final String SECRET = "secret";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
        ArgsUtils.rejectUnknownArgs(auth.getArgs(), SUBJECT, SECRET);
        var secret = ArgsUtils.requiredNonBlankArg(auth.getArgs(), SUBJECT, SECRET);
        return Map.of(HEADER, new StringBuilder(secret).reverse().toString());
    }
}

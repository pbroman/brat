package dev.pbroman.brat.core.data;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_APIKEY;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BASIC;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BEARER;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_NONE;

/**
 * Authentication configuration for a request: a {@code type} plus the fields that type needs.
 * <p>
 * {@code final}: a new auth mechanism is added by writing an {@code AuthHandler} for its
 * {@code type}, never by subclassing this. A subclass could not be reached from a suite file anyway
 * — an {@code auth:} block carries no discriminator the loader could dispatch on, so it always binds
 * to this type.
 */
@Getter
public final class Auth extends ConfigData {

    public static final List<String> AUTH_TYPES =
            List.of(AUTH_TYPE_NONE, AUTH_TYPE_BASIC, AUTH_TYPE_BEARER, AUTH_TYPE_APIKEY);

    private final String type;
    private final String username;
    private final String password;
    private final String token;

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code auth:} block to.
     *
     * @param type the auth type
     * @param username the username
     * @param password the password
     * @param token the token
     */
    @JsonCreator
    public Auth(String type, String username, String password, String token) {
        this(type, username, password, token, null);
    }

    /**
     * {@code token} and {@code outcomes} default to {@code null}.
     *
     * @param type the auth type
     * @param username the username
     * @param password the password
     */
    public Auth(String type, String username, String password) {
        this(type, username, password, null, null);
    }

    /**
     * {@code username}, {@code password}, and {@code outcomes} default to {@code null}.
     *
     * @param type the auth type
     * @param token the token
     */
    public Auth(String type, String token) {
        this(type, null, null, token, null);
    }

    /**
     * {@code type} defaults to {@code AUTH_TYPE_NONE}; {@code username}, {@code password},
     * {@code token}, and {@code outcomes} default to {@code null}.
     */
    public Auth() {
        this(AUTH_TYPE_NONE, null, null);
    }

    /**
     * Constructor for an interpolated {@link Auth} object with its named outcomes.
     *
     * @param type the auth type
     * @param username a username or {@code null}
     * @param password a password or {@code null}
     * @param token a token or {@code null}
     * @param outcomes the named interpolation outcomes
     */
    public Auth(
            String type, String username, String password, String token, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.type = type;
        this.username = username;
        this.password = password;
        this.token = token;
    }
}

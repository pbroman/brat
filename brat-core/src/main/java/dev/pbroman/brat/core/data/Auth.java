package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_APIKEY;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BASIC;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BEARER;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_NONE;

import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import lombok.Getter;
import lombok.Setter;

/**
 * Authentication configuration for a request: a {@code type} plus the fields that type needs.
 */
@Getter
@Setter
public class Auth extends ConfigData {

    public static final List<String> AUTH_TYPES = List.of(AUTH_TYPE_NONE, AUTH_TYPE_BASIC, AUTH_TYPE_BEARER, AUTH_TYPE_APIKEY);

    private String type;
    private String username;
    private String password;
    private String token;
    private String reportingString;

    /**
     * {@code reportingString} defaults to {@code null} (not yet an interpolated copy).
     *
     * @param type the auth type
     * @param username the username
     * @param password the password
     * @param token the token
     */
    public Auth(String type, String username, String password, String token) {
        this(type, username, password, token, null);
    }

    /**
     * {@code token} and {@code reportingString} default to {@code null}.
     *
     * @param type the auth type
     * @param username the username
     * @param password the password
     */
    public Auth(String type, String username, String password) {
        this(type, username, password, null, null);
    }

    /**
     * {@code username}, {@code password}, and {@code reportingString} default to {@code null}.
     *
     * @param type the auth type
     * @param token the token
     */
    public Auth(String type, String token) {
        this(type, null, null, token, null);
    }

    /**
     * {@code type} defaults to {@code AUTH_TYPE_NONE}; {@code username}, {@code password},
     * {@code token}, and {@code reportingString} default to {@code null}.
     */
    public Auth() {
        this(AUTH_TYPE_NONE, null, null);
    }

    /**
     * Constructor for an interpolated {@link Auth} object with a {@code reportingString}.
     *
     * @param type the auth type
     * @param username a username or {@code null}
     * @param password a password or {@code null}
     * @param token a token or {@code null}
     * @param reportingString a reporting string
     */
    public Auth(String type, String username, String password, String token, String reportingString) {
        this.type = type;
        this.username = username;
        this.password = password;
        this.token = token;
        this.reportingString = reportingString;
    }

    @Override
    public Auth interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (reportingString != null) {
            throw new BratException(String.format("This Auth (%s) is already an interpolated copy", this));
        }
        var typeOutcome = interpolation.outcome(type, runtimeData);
        var usernameOutcome = username == null ? null : interpolation.outcome(username, runtimeData);
        var passwordOutcome = password == null ? null : interpolation.outcome(password, runtimeData);
        var tokenOutcome = token == null ? null : interpolation.outcome(token, runtimeData);

        var report = new StringBuilder();
        appendToReport(report, "type", typeOutcome);
        appendToReport(report, "username", usernameOutcome);
        appendToReport(report, "password", passwordOutcome);
        appendToReport(report, "token", tokenOutcome);

        return new Auth(typeOutcome.asString(),
                usernameOutcome == null ? null : usernameOutcome.asString(),
                passwordOutcome == null ? null : passwordOutcome.asString(),
                tokenOutcome == null ? null : tokenOutcome.asString(),
                report.toString());
    }

    private void appendToReport(StringBuilder report, String field, InterpolationOutcome outcome) {
        if (outcome == null) {
            return;
        }
        if (!report.isEmpty()) {
            report.append(", ");
        }
        report.append(String.format("%s: ", field)).append(outcome.reportingString());
    }

}

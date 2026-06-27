package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_APIKEY;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BASIC;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_BEARER;
import static dev.pbroman.brat.core.util.Constants.AUTH_TYPE_NONE;

import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Auth extends ConfigData {

    public static final List<String> AUTH_TYPES = List.of(AUTH_TYPE_NONE, AUTH_TYPE_BASIC, AUTH_TYPE_BEARER, AUTH_TYPE_APIKEY);

    private String type;
    private String username;
    private String password;
    private String token;
    private final Auth nonInterpolated;

    public Auth(String type, String username, String password, String token) {
        this(type, username, password, token, null);
    }

    public Auth(String type, String username, String password) {
        this(type, username, password, null, null);
    }

    public Auth(String type, String token) {
        this(type, null, null, token, null);
    }

    public Auth() {
        this(AUTH_TYPE_NONE, null, null);
    }

    public Auth(String type, String username, String password, String token, Auth nonInterpolated) {
        this.type = type;
        this.username = username;
        this.password = password;
        this.token = token;
        this.nonInterpolated = nonInterpolated;
    }

    public Auth interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (nonInterpolated != null) {
            throw new IllegalStateException(String.format("This Auth (%s) is already an interpolated copy", this));
        }
        return new Auth(type,
                interpolation.interpolate(username, runtimeData),
                interpolation.interpolate(password, runtimeData),
                interpolation.interpolate(token, runtimeData),
                this);
    }

}

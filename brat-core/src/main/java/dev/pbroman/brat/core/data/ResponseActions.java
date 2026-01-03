package dev.pbroman.brat.core.data;

import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * Data class containing actions to be performed on a response, that is assertions and setVars requests.
 */
@Getter
public class ResponseActions {

    private final List<Assertion> assertions;
    private final Map<String, String> setVars;

    public ResponseActions(List<Assertion> assertions, Map<String, String> setVars) {
        this.assertions = assertions == null ? List.of() : assertions;
        this.setVars = setVars == null ? Map.of() : setVars;
    }

}

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

    /**
     * Constructs the actions to take on a response, treating {@code null} as "none" for both.
     *
     * @param assertions the assertions to resolve, or {@code null} for none
     * @param setVars the variables to set from the response, or {@code null} for none
     */
    public ResponseActions(List<Assertion> assertions, Map<String, String> setVars) {
        this.assertions = assertions == null ? List.of() : assertions;
        this.setVars = setVars == null ? Map.of() : setVars;
    }
}

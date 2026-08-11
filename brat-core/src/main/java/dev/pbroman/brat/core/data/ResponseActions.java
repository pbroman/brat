package dev.pbroman.brat.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * Data class containing actions to be performed on a response, that is assertions and setVars requests.
 * <p>
 * {@code final}: a third kind of response action is not addable today, and when it becomes addable it
 * will be through a discriminated entry this type reads, never through a subclass — a
 * {@code responseActions:} block binds to this type with nothing to dispatch on.
 */
@Getter
public final class ResponseActions {

    private final List<Assertion> assertions;
    private final Map<String, String> setVars;

    /**
     * Constructs the actions to take on a response, treating {@code null} as "none" for both.
     *
     * @param assertions the assertions to resolve, or {@code null} for none
     * @param setVars the variables to set from the response, or {@code null} for none
     */
    @JsonCreator
    public ResponseActions(List<Assertion> assertions, Map<String, String> setVars) {
        this.assertions = assertions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(assertions));
        this.setVars = setVars == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(setVars));
    }
}

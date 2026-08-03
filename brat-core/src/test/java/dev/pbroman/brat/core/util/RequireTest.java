package dev.pbroman.brat.core.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class RequireTest {

    @Test
    void nonNull_doesNothingForANonNullValue() {
        // when / then
        assertThatCode(() -> Require.nonNull("a value", "must be set")).doesNotThrowAnyException();
    }

    @Test
    void nonNull_throwsWithTheGivenMessageForANullValue() {
        // when / then
        assertThatThrownBy(() -> Require.nonNull(null, "the value must be set"))
                .isInstanceOf(BratException.class)
                .hasMessage("the value must be set");
    }

    @Test
    void noNullElements_doesNothingForAListWithoutNulls() {
        // when / then
        assertThatCode(() -> Require.noNullElements(List.of("a", "b"), "must be set"))
                .doesNotThrowAnyException();
    }

    @Test
    void noNullElements_doesNothingForAnEmptyList() {
        // when / then
        assertThatCode(() -> Require.noNullElements(List.of(), "must be set")).doesNotThrowAnyException();
    }

    @Test
    void noNullElements_throwsWithTheGivenMessageForANullList() {
        // when / then
        assertThatThrownBy(() -> Require.noNullElements(null, "the list must be set"))
                .isInstanceOf(BratException.class)
                .hasMessage("the list must be set");
    }

    @Test
    void noNullElements_throwsWithTheGivenMessageForAListHoldingNull() {
        // given
        var list = new ArrayList<String>();
        list.add("a");
        list.add(null);

        // when / then
        assertThatThrownBy(() -> Require.noNullElements(list, "no element may be null"))
                .isInstanceOf(BratException.class)
                .hasMessage("no element may be null");
    }
}

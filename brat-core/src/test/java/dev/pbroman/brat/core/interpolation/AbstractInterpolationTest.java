package dev.pbroman.brat.core.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.properties.InterpolationProperties;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;

public abstract class AbstractInterpolationTest {

    protected static final String mockResult = "mockResult";

    protected static final String nonMatchingPattern = "${bollocks}";

    protected final InterpolationProperties properties = new InterpolationProperties();

    protected final InterpolationTools tools = new InterpolationTools(properties);

    protected RuntimeData runtimeData;

    protected final Interpolation mockRule = Mockito.mock(Interpolation.class);

    protected Interpolation underTest;

    @BeforeEach
    void basicSetUp() throws Exception {
        when(mockRule.interpolate(Mockito.anyString(), Mockito.any())).thenReturn(mockResult);
        when(mockRule.interpolate(eq(nonMatchingPattern), Mockito.any())).thenReturn(nonMatchingPattern);
        runtimeData = setUpRuntimeData();
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of("moo", "baa"), Map.of());
    }

    @Test
    void inputNull_throwsNullPointerException() {
        assertThatThrownBy(() -> {
            underTest.interpolate(null, runtimeData);
        }).isInstanceOf(NullPointerException.class);
    }

    @Test
    void runtimeDataNull_throwsNullPointerException() {
        assertThatThrownBy(() -> {
            underTest.interpolate("moo", null);
        }).isInstanceOf(NullPointerException.class);
    }

    @Test
    void noMatchingPattern_returnsInput() throws Exception{
        // given
        var input = "${bollocks}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

}
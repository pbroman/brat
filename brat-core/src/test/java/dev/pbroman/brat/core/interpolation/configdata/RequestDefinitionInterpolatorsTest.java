package dev.pbroman.brat.core.interpolation.configdata;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RequestDefinitionInterpolatorsTest {

    private final Interpolation interpolation =
            (input, runtimeData) -> new InterpolationOutcome(input + "-i", input + "-i");
    private final RuntimeData runtimeData = mock(RuntimeData.class);

    private final RequestDefinitionInterpolators underTest =
            new RequestDefinitionInterpolators(List.of(new HttpRequestDefinitionInterpolator(new AuthInterpolator())));

    @Test
    void interpolated_usesTheInterpolatorRegisteredForTheDefinitionsClass() {
        // given
        var definition = new HttpRequestDefinition("http://url", "GET", null, null, null, null);

        // when
        var interpolated = underTest.interpolated(definition, interpolation, runtimeData);

        // then - a fresh copy, interpolated, not the instance handed in
        assertThat(interpolated).isNotSameAs(definition).isInstanceOf(HttpRequestDefinition.class);
        assertThat(((HttpRequestDefinition) interpolated).getUrl()).isEqualTo("http://url-i");
    }

    @Test
    void interpolated_throwsForADefinitionNoInterpolatorIsRegisteredFor() {
        // given - the subclass trap: its own fields would otherwise keep their raw ${...} tokens
        var subclassed = new SubclassedDefinition();

        // when / then
        assertThatThrownBy(() -> underTest.interpolated(subclassed, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(SubclassedDefinition.class.getName());
    }

    @Test
    void interpolated_throwsForADefinitionThatIsNotConfigData() {
        // given - a definition has to be interpolatable, which the api's bound states and this enforces
        RequestDefinition notConfigData = () -> "stub";
        var registry = new RequestDefinitionInterpolators(List.of(new StubInterpolator()));

        // when / then
        assertThatThrownBy(() -> registry.interpolated(notConfigData, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsForANullDefinition() {
        assertThatThrownBy(() -> underTest.interpolated(null, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void has_answersForTheExactClassOnly() {
        // then - lookup is exact, which is the other half of the subclass guarantee
        assertThat(underTest.has(HttpRequestDefinition.class)).isTrue();
        assertThat(underTest.has(SubclassedDefinition.class)).isFalse();
    }

    @Test
    void constructor_lastRegistrationWinsForOneDefinitionType() {
        // given - the overlay a consumer replacing a built-in interpolator depends on
        var replacement = new ReplacementHttpInterpolator();

        // when
        var registry = new RequestDefinitionInterpolators(
                List.of(new HttpRequestDefinitionInterpolator(new AuthInterpolator()), replacement));

        // then
        var interpolated = registry.interpolated(
                new HttpRequestDefinition("http://url", "GET", null, null, null, null), interpolation, runtimeData);
        assertThat(((HttpRequestDefinition) interpolated).getUrl()).isEqualTo("replaced");
    }

    @Test
    void constructor_throwsForNullsAmongTheInterpolators() {
        assertThatThrownBy(() -> new RequestDefinitionInterpolators(null)).isInstanceOf(BratException.class);
        var withNull = new java.util.ArrayList<RequestDefinitionInterpolator<?>>();
        withNull.add(null);
        assertThatThrownBy(() -> new RequestDefinitionInterpolators(withNull)).isInstanceOf(BratException.class);
    }

    /** A definition an extender might write by subclassing, which is exactly what must not resolve. */
    private static final class SubclassedDefinition extends ConfigData implements RequestDefinition {

        private SubclassedDefinition() {
            super(null);
        }

        @Override
        public String protocol() {
            return "http";
        }
    }

    /** Registered for a type nothing in a test constructs, so a lookup can miss deliberately. */
    private static final class StubInterpolator implements RequestDefinitionInterpolator<SubclassedDefinition> {

        @Override
        public Class<SubclassedDefinition> definitionType() {
            return SubclassedDefinition.class;
        }

        @Override
        public SubclassedDefinition interpolated(
                SubclassedDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
            return target;
        }
    }

    /** A second interpolator for HTTP, to prove which of two registrations survives. */
    private static final class ReplacementHttpInterpolator
            implements RequestDefinitionInterpolator<HttpRequestDefinition> {

        @Override
        public Class<HttpRequestDefinition> definitionType() {
            return HttpRequestDefinition.class;
        }

        @Override
        public HttpRequestDefinition interpolated(
                HttpRequestDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
            return new HttpRequestDefinition("replaced", "GET", null, null, null, null, Map.of());
        }
    }
}

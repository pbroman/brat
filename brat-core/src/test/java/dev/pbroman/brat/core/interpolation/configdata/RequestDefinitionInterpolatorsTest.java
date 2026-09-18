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
        // given - the interface's intersection bound means only an unchecked registration can get
        // here, which is exactly what this interpolator does: it declares one type and answers with
        // another. The message has to name the class, since a plugin author sees nothing else
        var registry = new RequestDefinitionInterpolators(List.of(new MisdeclaringInterpolator()));

        // when / then
        assertThatThrownBy(() -> registry.interpolated(new NotConfigData(), interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(NotConfigData.class.getName())
                .hasMessageContaining("ConfigData");
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

    /** A request definition that forgot to be {@link ConfigData}, which no bound can stop at runtime. */
    private static final class NotConfigData implements RequestDefinition {

        @Override
        public String protocol() {
            return "stub";
        }
    }

    /**
     * Registers itself for a class that is not a {@link ConfigData}, which the interface's bound
     * forbids and an unchecked cast allows anyway — the only route to the guard this exercises.
     */
    private static final class MisdeclaringInterpolator implements RequestDefinitionInterpolator<SubclassedDefinition> {

        @Override
        @SuppressWarnings("unchecked")
        public Class<SubclassedDefinition> definitionType() {
            return (Class<SubclassedDefinition>) (Class<?>) NotConfigData.class;
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

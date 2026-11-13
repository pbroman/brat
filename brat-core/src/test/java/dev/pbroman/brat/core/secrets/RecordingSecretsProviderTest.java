package dev.pbroman.brat.core.secrets;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingSecretsProviderTest {

    private final ValueMask mask = new ValueMask();
    private final RecordingSecretsProvider underTest =
            new RecordingSecretsProvider(new MapSecretsProvider(Map.of("dbPassword", "hunter2")), mask);

    @Test
    void constructor_throwsForANullProvider() {
        // when / then
        assertThatThrownBy(() -> new RecordingSecretsProvider(null, mask)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullMask() {
        // when / then
        assertThatThrownBy(() -> new RecordingSecretsProvider(new MapSecretsProvider(Map.of()), null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void getSecret_answersAsTheWrappedProvider() {
        // when / then
        assertThat(underTest.getSecret("dbPassword")).contains("hunter2");
        assertThat(underTest.getSecret("missing")).isEmpty();
    }

    @Test
    void getSecret_addsAReturnedValueToTheMaskAsASecret() {
        // when
        underTest.getSecret("dbPassword");

        // then - a secret stays masked even when guessed values are shown
        assertThat(mask.mask("password=hunter2", true)).isEqualTo("password=***");
    }

    @Test
    void getSecret_addsNothingWhenNoValueIsReturned() {
        // given
        var recording = new RecordingSecretsProvider(key -> Optional.empty(), mask);

        // when
        recording.getSecret("hunter2");

        // then - the key asked for is not a value
        assertThat(mask.mask("hunter2", false)).isEqualTo("hunter2");
    }

    @Test
    void getSecret_propagatesWhatTheWrappedProviderThrows() {
        // given
        SecretsProvider failing = key -> {
            throw new BratException("vault unreachable");
        };
        var recording = new RecordingSecretsProvider(failing, mask);

        // when / then
        assertThatThrownBy(() -> recording.getSecret("dbPassword")).hasMessage("vault unreachable");
    }

    @Test
    void close_leavesTheWrappedProviderOpen() {
        // given
        var closed = new AtomicBoolean();
        SecretsProvider wrapped = new SecretsProvider() {
            @Override
            public Optional<String> getSecret(String key) {
                return Optional.empty();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        // when
        new RecordingSecretsProvider(wrapped, mask).close();

        // then
        assertThat(closed).isFalse();
    }
}

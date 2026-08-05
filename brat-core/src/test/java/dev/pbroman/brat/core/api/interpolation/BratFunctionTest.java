package dev.pbroman.brat.core.api.interpolation;

import java.util.List;
import java.util.function.Function;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BratFunctionTest {

    @Test
    void of_reportsTheGivenName() {
        // when
        var function = BratFunction.of("upper", args -> args.getFirst().toUpperCase());

        // then
        assertThat(function.name()).isEqualTo("upper");
    }

    @Test
    void of_delegatesToTheImplementation() {
        // given
        var function = BratFunction.of("join", args -> String.join("-", args));

        // when
        var result = function.apply(List.of("a", "b"));

        // then
        assertThat(result).isEqualTo("a-b");
    }

    @Test
    void of_passesTheArgumentsThroughUnchanged() {
        // given
        var function = BratFunction.of("echo", args -> String.valueOf(args));

        // when
        var result = function.apply(List.of("", "  ", "x"));

        // then — empty and blank arguments are the implementation's business, not of()'s
        assertThat(result).isEqualTo("[,   , x]");
    }

    @Test
    void of_doesNotValidateTheName() {
        // when / then — the registry is the single validator, so a name that will be rejected there
        // is still constructible here; a second validator would be free to drift from the first
        assertThatCode(() -> BratFunction.of(null, args -> "")).doesNotThrowAnyException();
        assertThatCode(() -> BratFunction.of("  ", args -> "")).doesNotThrowAnyException();
        assertThatCode(() -> BratFunction.of("__upper", args -> "")).doesNotThrowAnyException();
    }

    @Test
    void of_describesItselfByNameAndImplementationOrigin() {
        // when
        var function = BratFunction.of("upper", args -> "");

        // then — every of() function shares one anonymous class, so the class identifies nothing;
        // the implementation's declaring class is what tells core apart from a plugin in a log line
        assertThat(function.toString()).contains("upper").contains(BratFunctionTest.class.getName());
    }

    @Test
    void of_stripsTheSyntheticSuffixFromALambdaOrigin() {
        // when
        var function = BratFunction.of("upper", args -> "");

        // then — the hex in `…$$Lambda/0x00007f…` changes per run, which would make one override
        // look like several across two log lines
        assertThat(function.toString()).doesNotContain("$$Lambda").doesNotContain("0x");
    }

    @Test
    void of_describesAnImplementationWrittenAsAClassByItsOwnName() {
        // given — a plugin may write the implementation as a class rather than a lambda, and then
        // there is no synthetic suffix to strip
        var function = BratFunction.of("upper", new Shout());

        // then
        assertThat(function.toString()).contains(Shout.class.getName());
    }

    @Test
    void of_throwsForANullImplementation() {
        // when / then — no registry can detect this without calling the function, so it has to fail
        // here rather than as a NullPointerException from inside the returned object much later
        assertThatThrownBy(() -> BratFunction.of("upper", null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("upper");
    }

    @Test
    void of_reportsANullNameRatherThanSubstitutingOne() {
        // when
        var function = BratFunction.of(null, args -> "");

        // then — the registry needs to see the bad name to report it
        assertThat(function.name()).isNull();
    }

    private static final class Shout implements Function<List<String>, String> {

        @Override
        public String apply(List<String> args) {
            return args.getFirst().toUpperCase();
        }
    }
}

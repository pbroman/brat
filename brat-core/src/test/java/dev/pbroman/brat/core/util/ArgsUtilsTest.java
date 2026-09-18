package dev.pbroman.brat.core.util;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArgsUtilsTest {

    private static final String SUBJECT = "the 'mtls' request handler";

    @Test
    void rejectUnknownArgs_acceptsEveryLegalKey() {
        // given
        var args = Map.of("min", "1", "max", "9");

        // then
        assertThatCode(() -> ArgsUtils.rejectUnknownArgs(args, SUBJECT, "min", "max"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectUnknownArgs_acceptsAnEmptyBagWhateverIsLegal() {
        // given - the ordinary case: nothing was authored
        assertThatCode(() -> ArgsUtils.rejectUnknownArgs(Map.of(), SUBJECT, "min"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ArgsUtils.rejectUnknownArgs(Map.of(), SUBJECT)).doesNotThrowAnyException();
    }

    @Test
    void rejectUnknownArgs_rejectsAnyKeyWhenTheOwnerTakesNone() {
        // given - a handler that understands no arguments, which is core's own
        var args = Map.of("certAlias", "client-a");

        // then - "takes no args" rather than an empty list of known ones: there is no key to correct,
        // the args belong to a handler this request did not select
        assertThatThrownBy(() -> ArgsUtils.rejectUnknownArgs(args, SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("certAlias")
                .hasMessageContaining(SUBJECT)
                .hasMessageContaining("takes no args");
    }

    @Test
    void rejectUnknownArgs_namesTheOffendingKeysInAStableOrder() {
        // given - a bag may be any Map, and a HashMap's iteration order is not the author's, so the
        // same suite would otherwise report two different messages on two runs
        var args = new HashMap<String, String>();
        args.put("zeta", "1");
        args.put("alpha", "2");

        // then
        assertThatThrownBy(() -> ArgsUtils.rejectUnknownArgs(args, SUBJECT, "min"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("[alpha, zeta]");
    }

    @Test
    void rejectUnknownArgs_namesTheOffendingKeysAndWhatIsAccepted() {
        // given - a typo is the case this exists for: a bag swallows it and a typed field would not
        var args = new HashMap<String, String>();
        args.put("ofset", "0.01");
        args.put("mni", "1");

        // then
        assertThatThrownBy(() -> ArgsUtils.rejectUnknownArgs(args, SUBJECT, "offset", "min"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ofset")
                .hasMessageContaining("mni")
                .hasMessageContaining("offset");
    }

    @Test
    void rejectUnknownArgs_neverQuotesAValue() {
        // given - an argument may hold a secret, and a message that echoes one leaks it into a report
        var args = Map.of("token", "s3cr3t-token");

        // then
        assertThatThrownBy(() -> ArgsUtils.rejectUnknownArgs(args, SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageNotContaining("s3cr3t-token");
    }

    @Test
    void requiredArg_returnsTheValue() {
        // given
        var args = Map.of("offset", "0.01");

        // then
        assertThat(ArgsUtils.requiredArg(args, SUBJECT, "offset")).isEqualTo("0.01");
    }

    @Test
    void requiredArg_throwsWhenItIsAbsent() {
        // then - naming the subject, since the same message serves funcs and handlers alike
        assertThatThrownBy(() -> ArgsUtils.requiredArg(Map.of(), SUBJECT, "offset"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("offset")
                .hasMessageContaining(SUBJECT);
    }

    @Test
    void requiredArg_throwsWhenItIsPresentButNull() {
        // given - authored YAML is where nulls come from: `offset:` with nothing after it
        var args = new HashMap<String, String>();
        args.put("offset", null);

        // then
        assertThatThrownBy(() -> ArgsUtils.requiredArg(args, SUBJECT, "offset")).isInstanceOf(BratException.class);
    }
}

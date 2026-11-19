package dev.pbroman.brat.core.rendering;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTextTest {

    private final ValueMask mask = new ValueMask();

    @Test
    void shown_returnsShortTextWhole() {
        // when
        var shown = ReportText.shown("{\"name\": \"bob\"}", mask, false);

        // then
        assertThat(shown).isEqualTo("{\"name\": \"bob\"}");
    }

    @Test
    void shown_masksTheText() {
        // given
        mask.addSecret("hunter2");
        mask.addGuessed("eyJhbGciOi");

        // when
        var shown = ReportText.shown("hunter2 eyJhbGciOi", mask, false);

        // then
        assertThat(shown).isEqualTo("*** ***");
    }

    @Test
    void shown_passesShowSecretsToTheMask() {
        // given
        mask.addSecret("hunter2");
        mask.addGuessed("eyJhbGciOi");

        // when
        var shown = ReportText.shown("hunter2 eyJhbGciOi", mask, true);

        // then
        assertThat(shown).isEqualTo("*** eyJhbGciOi");
    }

    @Test
    void shown_returnsTextOfExactlyTheMaximumLengthWhole() {
        // given
        var text = "x".repeat(ReportText.MAX_LENGTH);

        // when
        var shown = ReportText.shown(text, mask, false);

        // then
        assertThat(shown).isEqualTo(text);
    }

    @Test
    void shown_cutsLongerTextAndSaysHowMuchWasCut() {
        // given
        var text = "x".repeat(ReportText.MAX_LENGTH + 1234);

        // when
        var shown = ReportText.shown(text, mask, false);

        // then
        assertThat(shown)
                .isEqualTo("x".repeat(ReportText.MAX_LENGTH) + System.lineSeparator() + "… 1234 more characters");
    }

    @Test
    void shown_masksBeforeCutting() {
        // given - a secret straddling the cut, which a cut first would leave half visible
        var secret = "s3cr3t-value";
        mask.addSecret(secret);
        // 4092 + 12 + 1 = 4105 characters as written, 4092 + 3 + 1 = 4096 once masked
        var text = "x".repeat(ReportText.MAX_LENGTH - 4) + secret + "y";

        // when
        var shown = ReportText.shown(text, mask, false);

        // then - masked, the text is shorter than the limit, so nothing is cut
        assertThat(shown).doesNotContain("s3cr").doesNotContain("value").contains("***");
        assertThat(shown).doesNotContain("more characters");
    }

    @Test
    void shown_neverSplitsASurrogatePair() {
        // given - the last character kept would be the first half of an emoji
        var text = "x".repeat(ReportText.MAX_LENGTH - 1) + "😀" + "y";

        // when
        var shown = ReportText.shown(text, mask, false);

        // then
        assertThat(shown).startsWith("x".repeat(ReportText.MAX_LENGTH - 1) + System.lineSeparator());
        assertThat(shown).endsWith("… 3 more characters");
    }

    @Test
    void shown_throwsForNullText() {
        // when / then
        assertThatThrownBy(() -> ReportText.shown(null, mask, false)).isInstanceOf(BratException.class);
    }

    @Test
    void shown_throwsForANullMask() {
        // when / then
        assertThatThrownBy(() -> ReportText.shown("text", null, false)).isInstanceOf(BratException.class);
    }
}

package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new FormatConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
        "isUuid,3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        "isUuid,3F2504E0-4F89-11D3-9A0C-0305E82C3301",
        "isEmail,john@example.com",
        "isEmail,john.doe+tag@mail.example.co.uk",
        "isIsoDateTime,2026-08-03T10:15:30",
        "isIsoDateTime,2026-08-03T10:15:30Z",
        "isIsoDateTime,2026-08-03T10:15:30+01:00",
        "isUrl,https://example.com/orders/1",
        "isUrl,http://localhost:8080",
        // an underscored authority is invalid to the letter of the spec, everyday in containers
        "isUrl,http://order_service:8080/health",
    })
    void resolve_isTrueForAWellFormedValue(String func, String a) {
        // when
        var result = resolver.resolve(new Condition(func, a));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "isUuid,not-a-uuid",
        "isUuid,3f2504e0-4f89-11d3-9a0c", // too few groups
        "isUuid,3f2504e04f8911d39a0c0305e82c3301", // unhyphenated
        "isEmail,john.example.com", // no @
        "isEmail,john@example", // no dot in the domain
        "isIsoDateTime,2026-08-03", // a date is not a date-time
        "isIsoDateTime,10:15:30", // nor is a time
        "isUrl,/orders/1", // relative
        "isUrl,mailto:john@example.com", // absolute but hostless
    })
    void resolve_isFalseForAMalformedValue(String func, String a) {
        // when
        var result = resolver.resolve(new Condition(func, a));

        // then
        assertThat(result).contains(false);
    }

    @ParameterizedTest
    @CsvSource({
        "isNotUuid,not-a-uuid",
        "notIsEmail,nope",
        "!isUrl,/orders/1",
    })
    void resolve_negatesWithEverySpelling(String func, String a) {
        // when
        var result = resolver.resolve(new Condition(func, a));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"isUuid", "isEmail", "isIsoDateTime", "isUrl"})
    void resolve_throwsForASequenceNamingTheFuncButNotTheContent(String func) {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition(func, List.of("s3cr3t"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'" + func + "'")
                .hasMessageContaining("a sequence")
                .hasMessageNotContaining("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"isNotUuid", "isNotEmail", "isNotIsoDateTime", "isNotUrl"})
    void resolve_throwsForAMappingEvenNegated(String func) {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition(func, Map.of("key", "s3cr3t"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("a mapping")
                .hasMessageNotContaining("s3cr3t");
    }
}

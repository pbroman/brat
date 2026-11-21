package dev.pbroman.brat.openapi;

import java.util.List;
import java.util.Set;

import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImportNamesTest {

    private static Operation operation(String method, String path, String operationId, String summary) {
        return new Operation(
                method, path, operationId, summary, List.of(), List.of(), List.of(), List.of(), null, List.of());
    }

    // --- fileName ---

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Orders API|orders-api",
                "GET orders {orderId}|get-orders-orderid",
                "  Café / Crème  |cafe-creme",
                "import orders|import-orders",
                "v2.1_beta|v2-1-beta",
                "--already--|already"
            })
    void fileName_keepsLowerCaseAsciiLettersAndDigitsJoinedByHyphens(String text, String expected) {
        // when
        var name = ImportNames.fileName(text);

        // then
        assertThat(name).isEqualTo(expected);
    }

    @Test
    void fileName_isEmptyWhenNothingIsLeft() {
        // when / then
        assertThat(ImportNames.fileName("??? !!!")).isEmpty();
        assertThat(ImportNames.fileName("")).isEmpty();
        assertThat(ImportNames.fileName(null)).isEmpty();
    }

    // --- nodeName ---

    @Test
    void nodeName_replacesSlashesAndCollapsesWhitespace() {
        // when
        var name = ImportNames.nodeName("  orders/import \t now  ");

        // then
        assertThat(name).isEqualTo("orders import now");
    }

    @Test
    void nodeName_isEmptyForNullOrOnlySlashesAndWhitespace() {
        // when / then
        assertThat(ImportNames.nodeName(null)).isEmpty();
        assertThat(ImportNames.nodeName(" / ")).isEmpty();
    }

    // --- requestName ---

    @Test
    void requestName_prefersTheSummary() {
        // when
        var name = ImportNames.requestName(operation("GET", "/orders", "listOrders", "list orders"));

        // then
        assertThat(name).isEqualTo("list orders");
    }

    @Test
    void requestName_fallsBackToTheOperationId() {
        // when
        var name = ImportNames.requestName(operation("GET", "/orders", "listOrders", null));

        // then
        assertThat(name).isEqualTo("listOrders");
    }

    @Test
    void requestName_fallsBackToMethodAndPath() {
        // when
        var name = ImportNames.requestName(operation("GET", "/orders/{orderId}", null, null));

        // then
        assertThat(name).isEqualTo("GET orders {orderId}");
    }

    @Test
    void requestName_treatsABlankOrSlashOnlySummaryAsAbsent() {
        // when
        var blank = ImportNames.requestName(operation("GET", "/orders", "listOrders", "  "));
        var slash = ImportNames.requestName(operation("GET", "/orders", null, "/"));

        // then
        assertThat(blank).isEqualTo("listOrders");
        assertThat(slash).isEqualTo("GET orders");
    }

    @Test
    void requestName_isANodeName() {
        // when
        var name = ImportNames.requestName(operation("POST", "/orders", null, "create /  an order"));

        // then
        assertThat(name).isEqualTo("create an order");
    }

    @Test
    void requestName_ofTheRootPathIsTheMethod() {
        // when
        var name = ImportNames.requestName(operation("GET", "/", null, null));

        // then
        assertThat(name).isEqualTo("GET");
    }

    // --- unique ---

    @Test
    void unique_keepsNamesThatAreAlreadyUnique() {
        // when
        var names = ImportNames.unique(List.of("a", "b"), " ", Set.of());

        // then
        assertThat(names).containsExactly("a", "b");
    }

    @Test
    void unique_numbersRepeatsFromTwo() {
        // when
        var names = ImportNames.unique(List.of("a", "a", "a"), " ", Set.of());

        // then
        assertThat(names).containsExactly("a", "a 2", "a 3");
    }

    @Test
    void unique_skipsANumberALaterNameHas() {
        // when
        var names = ImportNames.unique(List.of("a", "a", "a 2"), " ", Set.of());

        // then
        assertThat(names).containsExactly("a", "a 3", "a 2");
    }

    @Test
    void unique_avoidsTakenNames() {
        // when
        var names = ImportNames.unique(List.of("bodies", "local"), "-", Set.of("bodies", "bodies-2"));

        // then
        assertThat(names).containsExactly("bodies-3", "local");
    }

    @Test
    void unique_doesNotModifyItsArguments() {
        // given
        var names = List.of("a", "a");
        var taken = Set.of("b");

        // when
        ImportNames.unique(names, "-", taken);

        // then
        assertThat(names).containsExactly("a", "a");
        assertThat(taken).containsExactly("b");
    }
}

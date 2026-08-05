package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncodingFunctionsTest {

    private static String call(String name, String... args) {
        return EncodingFunctions.functions().stream()
                .filter(function -> function.name().equals(name))
                .findFirst()
                .orElseThrow()
                .apply(List.of(args));
    }

    // --- base64 ---

    @Test
    void base64_encodesWithTheStandardAlphabetAndPadding() {
        // when
        var result = call("base64", "hello");

        // then
        assertThat(result).isEqualTo("aGVsbG8=");
    }

    @Test
    void base64_encodesTheUtf8BytesOfTheArgument() {
        // when — not the platform's default charset
        var result = call("base64", "é");

        // then
        assertThat(result).isEqualTo("w6k=");
    }

    @Test
    void base64Decode_reversesBase64() {
        // when
        var result = call("base64Decode", "aGVsbG8=");

        // then
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void base64Decode_throwsForInputThatIsNotBase64() {
        // when / then
        assertThatThrownBy(() -> call("base64Decode", "not base64!")).isInstanceOf(BratException.class);
    }

    @Test
    void base64_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("base64")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> call("base64", "a", "b")).isInstanceOf(BratException.class);
    }

    // --- url encoding ---

    @Test
    void urlEncode_percentEncodesASpaceRatherThanUsingAPlus() {
        // when — form encoding would give "a+b", which is wrong in a path segment
        var result = call("urlEncode", "a b");

        // then
        assertThat(result).isEqualTo("a%20b");
    }

    @Test
    void urlEncode_leavesTheUnreservedCharactersAlone() {
        // when — RFC 3986 unreserved set
        var result = call("urlEncode", "aZ09-._~");

        // then
        assertThat(result).isEqualTo("aZ09-._~");
    }

    @Test
    void urlEncode_encodesReservedCharactersAndNonAscii() {
        // when / then
        assertThat(call("urlEncode", "a/b?c=d&e")).isEqualTo("a%2Fb%3Fc%3Dd%26e");
        assertThat(call("urlEncode", "é")).isEqualTo("%C3%A9");
    }

    @Test
    void urlDecode_reversesUrlEncode() {
        // given — including a literal plus, which form decoding would turn into a space
        var original = "a b+c/d é";

        // when
        var result = call("urlDecode", call("urlEncode", original));

        // then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void urlDecode_keepsAPlusLiteral() {
        // when
        var result = call("urlDecode", "a+b");

        // then
        assertThat(result).isEqualTo("a+b");
    }

    @Test
    void urlDecode_throwsForBrokenPercentEncoding() {
        // when / then
        assertThatThrownBy(() -> call("urlDecode", "%ZZ")).isInstanceOf(BratException.class);
    }

    // --- digests ---

    @Test
    void md5_returnsTheLowerCaseHexDigest() {
        // when
        var result = call("md5", "hello");

        // then
        assertThat(result).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void sha256_returnsTheLowerCaseHexDigest() {
        // when
        var result = call("sha256", "hello");

        // then
        assertThat(result).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256_isDeterministicAndAlwaysLowerCaseHex() {
        // when
        var first = call("sha256", "é");
        var second = call("sha256", "é");

        // then
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(second);
    }

    @Test
    void hmacSha256_signsTheDataWithTheKey() {
        // when — RFC 4231 style fixed vector
        var result = call("hmacSha256", "key", "The quick brown fox jumps over the lazy dog");

        // then
        assertThat(result).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void hmacSha256_throwsForAnEmptyKey() {
        // when / then — the realistic case is a secret that failed to resolve
        assertThatThrownBy(() -> call("hmacSha256", "", "data")).isInstanceOf(BratException.class);
    }

    @Test
    void hmacSha256_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("hmacSha256", "key")).isInstanceOf(BratException.class);
    }
}

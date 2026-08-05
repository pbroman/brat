package dev.pbroman.brat.core.interpolation.functions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireArgCount;

/**
 * Functions that encode, decode or digest text.
 * <p>
 * Everything works over UTF-8 bytes, and every digest is returned as lower-case hex.
 *
 * <dl>
 *   <dt>{@code ${__base64(text)}}, {@code ${__base64Decode(text)}}</dt>
 *   <dd>Base64 with the standard alphabet and padding — {@code ${__base64(${secrets.apiKey})}} for a
 *       Basic auth header. Decoding rejects input that is not valid base64.</dd>
 *   <dt>{@code ${__urlEncode(text)}}, {@code ${__urlDecode(text)}}</dt>
 *   <dd><strong>Percent-encoding per RFC 3986</strong>, so a space becomes {@code %20} and only
 *       {@code A-Za-z0-9-._~} pass through. Not form encoding: a {@code +} is a literal plus in both
 *       directions, which keeps {@code __urlDecode(__urlEncode(x))} equal to {@code x}. Form encoding
 *       would render a space as {@code +} and be wrong in a path segment.</dd>
 *   <dt>{@code ${__md5(text)}}, {@code ${__sha256(text)}}</dt>
 *   <dd>Hex digests. <strong>{@code __md5} is for compatibility, never for security</strong> — it is
 *       here because services like Gravatar key on an MD5 of an email address.</dd>
 *   <dt>{@code ${__hmacSha256(key, data)}}</dt>
 *   <dd>A hex HMAC-SHA256 of {@code data} under {@code key}, for the request signing that AWS,
 *       Stripe and Twilio require.</dd>
 * </dl>
 */
public final class EncodingFunctions {

    private static final String UNRESERVED = "-._~";

    /** Held rather than rebuilt: {@code withUpperCase} allocates, and encoding runs per byte. */
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

    private EncodingFunctions() {
        // no instances
    }

    /**
     * The encoding and hashing functions.
     *
     * @return the functions
     */
    public static List<BratFunction> functions() {
        return List.of(
                BratFunction.of("base64", EncodingFunctions::base64),
                BratFunction.of("base64Decode", EncodingFunctions::base64Decode),
                BratFunction.of("urlEncode", EncodingFunctions::urlEncode),
                BratFunction.of("urlDecode", EncodingFunctions::urlDecode),
                BratFunction.of("md5", EncodingFunctions::md5),
                BratFunction.of("sha256", EncodingFunctions::sha256),
                BratFunction.of("hmacSha256", EncodingFunctions::hmacSha256));
    }

    private static String base64(List<String> args) {
        requireArgCount("base64", args, 1, 1);
        return Base64.getEncoder().encodeToString(args.getFirst().getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Decode(List<String> args) {
        requireArgCount("base64Decode", args, 1, 1);
        try {
            return new String(Base64.getDecoder().decode(args.getFirst()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BratException("__base64Decode was given something that is not valid base64", e);
        }
    }

    private static String urlEncode(List<String> args) {
        requireArgCount("urlEncode", args, 1, 1);
        var encoded = new StringBuilder();
        for (var b : args.getFirst().getBytes(StandardCharsets.UTF_8)) {
            var c = (char) (b & 0xFF);
            if (Character.isLetterOrDigit(c) && c < 128 || UNRESERVED.indexOf(c) >= 0) {
                encoded.append(c);
            } else {
                encoded.append('%').append(UPPER_HEX.toHexDigits(b));
            }
        }
        return encoded.toString();
    }

    private static String urlDecode(List<String> args) {
        requireArgCount("urlDecode", args, 1, 1);
        try {
            // A literal + must survive, so it is escaped before URLDecoder can read it as a space
            return URLDecoder.decode(args.getFirst().replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BratException("__urlDecode was given something that is not valid percent-encoding", e);
        }
    }

    private static String md5(List<String> args) {
        requireArgCount("md5", args, 1, 1);
        return digest("MD5", args.getFirst());
    }

    private static String sha256(List<String> args) {
        requireArgCount("sha256", args, 1, 1);
        return digest("SHA-256", args.getFirst());
    }

    private static String digest(String algorithm, String text) {
        try {
            var digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Unreachable in practice: MD5 and SHA-256 are required of every Java platform. Handled
            // because the exception is checked, and untestable for the same reason it cannot happen.
            throw new BratException(algorithm + " is not available in this JVM", e);
        }
    }

    private static String hmacSha256(List<String> args) {
        requireArgCount("hmacSha256", args, 2, 2);
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(args.getFirst().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(args.get(1).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Unreachable in practice, as above: HmacSHA256 is required of every Java platform
            throw new BratException("HmacSHA256 is not available in this JVM", e);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            // Both are reachable, and an empty key — a secret that failed to resolve — is the
            // realistic case: SecretKeySpec rejects it from its constructor with the unchecked one,
            // while Mac.init raises the checked one.
            throw new BratException("__hmacSha256 was given a key it cannot use", e);
        }
    }
}

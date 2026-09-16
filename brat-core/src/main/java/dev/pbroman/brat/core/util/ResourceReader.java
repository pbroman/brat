package dev.pbroman.brat.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads a resource identified by a location string into a {@code String}.
 * <p>
 * A {@code file:} prefix resolves against the filesystem. Everything else — a
 * {@code classpath:} prefix, or a bare path with no prefix at all — resolves against the
 * classloader. Other URL schemes (e.g. {@code http:}) are not supported and are treated as a
 * (failing) classpath lookup.
 */
public final class ResourceReader {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";

    private ResourceReader() {}

    /**
     * Reads the resource at {@code location} as a {@code String}, decoded as UTF-8.
     *
     * @param location - the resource location, per the class-level doc
     * @return the resource content
     * @throws BratException if the resource can't be found or read
     */
    public static String readFileToString(String location) {
        return readFileToString(location, UTF_8);
    }

    /**
     * Reads the resource at {@code location} as a {@code String}, decoded using {@code charset}.
     *
     * @param location - the resource location, per the class-level doc
     * @param charset - the charset to decode the resource's bytes with
     * @return the resource content
     * @throws BratException if the resource can't be found or read
     */
    public static String readFileToString(String location, Charset charset) {
        try (InputStream inputStream = openStream(location)) {
            return new String(inputStream.readAllBytes(), charset);
        } catch (IOException e) {
            throw new BratException("Unable to read resource: " + location, e);
        }
    }

    /**
     * Resolves {@code location} against the location of the suite document that named it.
     * <p>
     * A location carrying a {@code file:} or {@code classpath:} prefix is already absolute in its own
     * medium and is returned unchanged. A <strong>bare</strong> location is relative to
     * {@code suiteLocation}: everything up to and including its last {@code /} is kept and
     * {@code location} appended, so the result stays in the same medium the suite itself was loaded
     * from. A suite location with no {@code /} yields {@code location} unchanged, that being its own
     * directory.
     * <p>
     * A bare location is <em>always</em> relative, a leading {@code /} included — there is no bare
     * spelling for an absolute path, and {@code file:} is how one is written. No normalisation is
     * performed: a {@code ..} segment is passed to the underlying lookup as written, which the
     * filesystem honours and the classloader does not.
     *
     * @param location the location as authored, after interpolation; never {@code null} or blank
     * @param suiteLocation the location the suite document was loaded from, prefix and all, or
     *        {@code null} when the suite came from no location — a document held in memory
     * @return {@code location} unchanged if it carries a prefix or if {@code suiteLocation} names no
     *         directory; otherwise {@code suiteLocation}'s directory with {@code location} appended
     * @throws BratException if {@code location} is {@code null} or blank; or if {@code location} is
     *         bare and {@code suiteLocation} is {@code null}, since there is then nothing to resolve
     *         against — the message says so rather than guessing a working directory
     */
    public static String resolve(String location, String suiteLocation) {
        if (StringUtils.isBlank(location)) {
            throw new BratException("The location may not be blank");
        }
        if (location.startsWith(FILE_PREFIX) || location.startsWith(CLASSPATH_PREFIX)) {
            return location;
        }
        Require.nonNull(suiteLocation, String.format("The suiteLocation may not be null for location '%s'", location));
        var suitePath = suiteLocation.substring(0, suiteLocation.lastIndexOf('/') + 1);
        return suitePath + location;
    }

    /**
     * Whether a resource exists at {@code location}, without reading it.
     * <p>
     * The prefix vocabulary is {@link #readFileToString(String)}'s: {@code file:} against the
     * filesystem, a {@code classpath:} prefix or a bare path against the classloader. {@code location}
     * is used as given — call {@link #resolve(String, String)} first where a bare path means
     * <em>relative to the suite</em>.
     *
     * @param location the resource location, per the class-level doc; never {@code null} or blank
     * @return {@code true} if a readable resource exists there, {@code false} otherwise — including a
     *         path naming a directory, which cannot be read as content. Existence is decided without
     *         reading: an <strong>empty</strong> file exists, a directory does not
     * @throws BratException if {@code location} is {@code null} or blank
     */
    public static boolean exists(String location) {
        if (StringUtils.isBlank(location)) {
            throw new BratException("The location may not be blank");
        }
        if (location.startsWith(FILE_PREFIX)) {
            return Files.isRegularFile(Path.of(location.substring(FILE_PREFIX.length())));
        }
        var resourcePath =
                location.startsWith(CLASSPATH_PREFIX) ? location.substring(CLASSPATH_PREFIX.length()) : location;
        var url = ResourceReader.class.getClassLoader().getResource(resourcePath);
        if (url == null) {
            return false;
        }
        if (!"file".equals(url.getProtocol())) {
            // Inside a jar, where a directory entry is spelled with a trailing slash and a resource
            // that resolves at all is readable.
            return !url.getPath().endsWith("/");
        }
        try {
            return Files.isRegularFile(Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static InputStream openStream(String location) throws IOException {
        if (location.startsWith(FILE_PREFIX)) {
            return Files.newInputStream(Path.of(location.substring(FILE_PREFIX.length())));
        }
        var resourcePath =
                location.startsWith(CLASSPATH_PREFIX) ? location.substring(CLASSPATH_PREFIX.length()) : location;
        var inputStream = ResourceReader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        return inputStream;
    }
}

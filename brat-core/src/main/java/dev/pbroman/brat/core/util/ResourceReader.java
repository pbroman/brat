package dev.pbroman.brat.core.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;

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

    private ResourceReader() {
    }

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

    private static InputStream openStream(String location) throws IOException {
        if (location.startsWith(FILE_PREFIX)) {
            return Files.newInputStream(Path.of(location.substring(FILE_PREFIX.length())));
        }
        var resourcePath = location.startsWith(CLASSPATH_PREFIX)
                ? location.substring(CLASSPATH_PREFIX.length())
                : location;
        var inputStream = ResourceReader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        return inputStream;
    }

}

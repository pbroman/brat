package dev.pbroman.brat.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Resolves, checks and reads resources identified by a location string.
 * <p>
 * A {@code file:} prefix resolves against the filesystem. Everything else — a
 * {@code classpath:} prefix, or a bare path with no prefix at all — resolves against the
 * classloader. Other URL schemes (e.g. {@code http:}) are not supported and are treated as a
 * (failing) classpath lookup.
 * <p>
 * {@link #resolve(String, String)} turns a bare location into one of those, relative to the document
 * that named it; {@link #exists(String)} answers whether one names readable content, without reading
 * it; {@link #readFileToString(String)} reads it.
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
     * @throws BratException if the resource can't be found or read, including a location that names
     *         a directory rather than content
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
     * @throws BratException if the resource can't be found or read, including a location that names
     *         a directory rather than content
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
     * from. A suite location naming no directory contributes its prefix alone, so
     * {@code file:orders.yaml} resolves {@code order.json} to {@code file:order.json} — and a bare
     * suite location, carrying no prefix either, yields {@code location} unchanged.
     * <p>
     * A bare location is <em>always</em> relative, a leading {@code /} included — there is no bare
     * spelling for an absolute path, and {@code file:} is how one is written. No normalisation is
     * performed: a {@code ..} segment is passed to the underlying lookup as written, which the
     * filesystem honours and the classloader does not.
     *
     * @param location the location as authored, after interpolation; never {@code null} or blank
     * @param suiteLocation the location the suite document was loaded from, prefix and all, or
     *        {@code null} when the suite came from no location — a document held in memory
     * @return {@code location} unchanged if it carries a prefix; otherwise {@code suiteLocation}'s
     *         own prefix and directory with {@code location} appended, either of which may be empty
     * @throws BratException if {@code location} is {@code null} or blank; or if {@code location} is
     *         bare and {@code suiteLocation} is {@code null}, since there is then nothing to resolve
     *         against — the message says so rather than guessing a working directory
     */
    public static String resolve(String location, String suiteLocation) {
        Require.nonBlank(location, "The location may not be blank");
        if (location.startsWith(FILE_PREFIX) || location.startsWith(CLASSPATH_PREFIX)) {
            return location;
        }
        Require.nonNull(suiteLocation, String.format("The suiteLocation may not be null for location '%s'", location));
        var directoryEnd = suiteLocation.lastIndexOf('/') + 1;
        return suiteLocation.substring(0, Math.max(prefixLength(suiteLocation), directoryEnd)) + location;
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
     *         path naming a directory, which cannot be read as content, and one that resolves but
     *         cannot be inspected, a corrupt archive being the case that does — a decided absence and
     *         a failure to decide are one answer here. Existence is decided without reading: an
     *         <strong>empty</strong> file exists, a directory does not
     * @throws BratException if {@code location} is {@code null} or blank
     */
    public static boolean exists(String location) {
        Require.nonBlank(location, "The location may not be blank");
        if (location.startsWith(FILE_PREFIX)) {
            return Files.isRegularFile(filePath(location));
        }
        var resourcePath = resourcePath(location);
        var url = resourceUrl(resourcePath);
        if (url == null) {
            return false;
        }
        try {
            requireContent(url, resourcePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Opens the resource at {@code location} for reading.
     *
     * @param location the resource location, per the class-level doc
     * @return an open stream over the resource's bytes, which the caller closes
     * @throws IOException if no resource exists at {@code location}, or if it names a directory or
     *         anything else that is not a regular file. A missing {@code file:} path is left to
     *         {@link Files#newInputStream}, whose own exception names it
     */
    private static InputStream openStream(String location) throws IOException {
        if (location.startsWith(FILE_PREFIX)) {
            var path = filePath(location);
            // A directory opens as a stream of zero bytes rather than failing, which would otherwise
            // put an empty body on the wire.
            if (Files.exists(path) && !Files.isRegularFile(path)) {
                throw new IOException("Not a regular file: " + path);
            }
            return Files.newInputStream(path);
        }
        var resourcePath = resourcePath(location);
        var url = resourceUrl(resourcePath);
        if (url == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        requireContent(url, resourcePath);
        return url.openStream();
    }

    /**
     * Checks that a classloader-resolved URL names readable content rather than a directory.
     * <p>
     * {@link #exists(String)} and {@code openStream} both call this on their classpath branch, so
     * that a resource cannot be reported present and then read as nothing. Their {@code file:}
     * branches do not call it; each applies {@link Files#isRegularFile} directly, which is the same
     * rule stated in the one other place it is needed. A jar is asked for its own entry rather than
     * read for a trailing {@code /}: a classloader answers a directory lookup written without one
     * with a URL that carries none either, so how the lookup was spelled cannot decide this.
     *
     * @param url the resource's URL, as the classloader returned it
     * @param resourcePath the path that resolved to {@code url}, for the message
     * @throws IOException if {@code url} names a directory, names no entry at all, or cannot be
     *         opened or converted to a path, each with its own message
     */
    private static void requireContent(URL url, String resourcePath) throws IOException {
        if ("file".equals(url.getProtocol())) {
            Path path;
            try {
                path = Path.of(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Not a usable resource URL: " + resourcePath, e);
            }
            if (!Files.isRegularFile(path)) {
                throw new IOException("Not a regular file: " + resourcePath);
            }
            return;
        }
        if (url.openConnection() instanceof JarURLConnection jarConnection) {
            var entry = jarConnection.getJarEntry();
            if (entry == null) {
                throw new IOException("No entry in the archive for: " + resourcePath);
            }
            if (entry.isDirectory()) {
                throw new IOException("Not a file, but a directory: " + resourcePath);
            }
            return;
        }
        if (url.getPath().endsWith("/")) {
            throw new IOException("Not a file, but a directory: " + resourcePath);
        }
    }

    /**
     * Looks {@code resourcePath} up on the classloader this class resolves classpath resources with.
     * <p>
     * One place answers which classloader that is, so a lookup and a check of the same location
     * cannot disagree about what is on the classpath.
     *
     * @param resourcePath the classloader-relative path, per {@link #resourcePath(String)}
     * @return the resource's URL, or {@code null} if the classloader has no resource there
     */
    private static URL resourceUrl(String resourcePath) {
        return ResourceReader.class.getClassLoader().getResource(resourcePath);
    }

    /**
     * The classloader-relative path {@code location} names.
     *
     * @param location a location with a {@code classpath:} prefix, or a bare one
     * @return {@code location} with any {@code classpath:} prefix removed
     */
    private static String resourcePath(String location) {
        return location.startsWith(CLASSPATH_PREFIX) ? location.substring(CLASSPATH_PREFIX.length()) : location;
    }

    /**
     * The filesystem path {@code location} names.
     *
     * @param location a location carrying the {@code file:} prefix
     * @return {@code location} with the prefix removed
     */
    private static Path filePath(String location) {
        return Path.of(location.substring(FILE_PREFIX.length()));
    }

    /**
     * The length of the {@code file:} or {@code classpath:} prefix {@code location} carries.
     *
     * @param location the location to inspect
     * @return the prefix's length, or {@code 0} if {@code location} carries neither prefix
     */
    private static int prefixLength(String location) {
        if (location.startsWith(FILE_PREFIX)) {
            return FILE_PREFIX.length();
        }
        return location.startsWith(CLASSPATH_PREFIX) ? CLASSPATH_PREFIX.length() : 0;
    }
}

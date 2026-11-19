package dev.pbroman.brat.core.schema;

import java.nio.charset.StandardCharsets;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.ResourceReader;

/**
 * The JSON Schema of a suite document, as shipped with core: what an editor uses to complete and check
 * a {@code *.brat.yaml} file.
 */
public final class SuiteSchema {

    /** Where the schema lives on the classpath. */
    static final String RESOURCE = "dev/pbroman/brat/core/schema/brat-suite.schema.json";

    private SuiteSchema() {}

    /**
     * Returns the schema's text.
     *
     * @return the schema, a JSON Schema draft 2020-12 document, exactly as shipped and ending in a
     *         newline; never {@code null}
     * @throws BratException if the schema is missing from the classpath, which only a broken build
     *         produces
     */
    public static String json() {
        return json(RESOURCE);
    }

    /**
     * Returns the text of the resource at {@code resource}, read as UTF-8.
     *
     * @param resource a classpath resource name, without a leading {@code /}
     * @return the resource's text; never {@code null}
     * @throws BratException if there is no such resource, or it cannot be read; the message names
     *         {@code resource}
     */
    static String json(String resource) {
        if (!ResourceReader.exists(resource)) {
            throw new BratException("The resource does not exist: " + resource);
        }
        return ResourceReader.readFileToString(resource, StandardCharsets.UTF_8);
    }
}

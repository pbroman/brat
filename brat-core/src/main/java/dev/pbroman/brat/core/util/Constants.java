package dev.pbroman.brat.core.util;

import java.util.regex.Pattern;

/**
 * The string literals shared across {@code brat-core}: interpolation namespaces, condition func
 * names, auth types and request defaults.
 */
public final class Constants {

    private Constants() {
        // utility class
    }

    /** What a report shows in place of a secret or a masked value. */
    public static final String MASK = "***";

    /** The response namespace key holding the location of a redirect that was not followed. */
    public static final String REDIRECT_NOT_FOLLOWED = "redirectNotFollowed";

    public static final String CONSTANTS = "constants";
    public static final String HEADERS = "headers";
    public static final String ENV = "env";
    public static final String VARS = "vars";
    public static final String PARAMS = "params";
    public static final String SECRETS = "secrets";
    public static final String FALLBACK_DELIMITER = ":-";
    public static final String TOKEN_PREFIX = "${";
    public static final String CLASSPATH_PREFIX = "classpath:";
    public static final String FILE_PREFIX = "file:";
    public static final String TOKEN_SUFFIX = "}";

    /** The HTTP header that carries a request's credentials. */
    public static final String AUTHORIZATION = "Authorization";

    /** The protocol of a request core can execute without a plugin, and the default when none is declared. */
    public static final String HTTP = "http";

    /** The key inside a {@code requestDefinition:} block naming what kind of request it describes. */
    public static final String PROTOCOL = "protocol";

    public static final String RESPONSE = "response";
    public static final String RESPONSE_VARS = "responseVars";
    public static final String STATUS_CODE = "statusCode";
    public static final String BODY = "body";
    public static final String JSON = "json";
    public static final String VARIABLE_GROUP_NAME = "variableGroup";

    /** The namespace of {@code ${response.statusCode}}, which carries no key. */
    public static final String RESPONSE_STATUS_CODE = RESPONSE + "." + STATUS_CODE;

    /** The namespace of {@code ${response.body}}, which carries no key. */
    public static final String RESPONSE_BODY = RESPONSE + "." + BODY;

    /** The namespace of {@code ${response.headers.<name>}}, whose key is the header name. */
    public static final String RESPONSE_HEADERS = RESPONSE + "." + HEADERS;

    /** The namespace of {@code ${response.json.<jsonPath>}}, whose key is the JSONPath. */
    public static final String RESPONSE_JSON = RESPONSE + "." + JSON;

    public static final String FORMAT_UUID = "isUuid";
    public static final String FORMAT_EMAIL = "isEmail";
    public static final String FORMAT_ISO_DATE_TIME = "isIsoDateTime";
    public static final String FORMAT_URL = "isUrl";

    public static final String NUMBER_CONDITION = "Number";
    public static final String STRING_CONDITION = "String";
    public static final String BOOLEAN_CONDITION = "Boolean";
    public static final String DATE_CONDITION = "Date";
    public static final String FORMAT_CONDITION = "Format";
    public static final String JSON_CONDITION = "Json";

    public static final String DEFAULT_TIMEOUT_MS = "30000";

    /** The method a request is performed with when it declares none. */
    public static final String DEFAULT_METHOD = "GET";

    public static final Integer DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * Separates the segments of a node's path, which is built from the suite and request names from
     * the root down. {@code /} is illegal inside a name for this reason, so a path stays readable in
     * a report and typeable as {@code --select "happy path/create an order"}.
     */
    public static final String PATH_DELIMITER = "/";

    // Condition constants
    // Func names are the canonical spelling a suite author is offered; matching normalises them.
    public static final String NULL = "null";
    public static final String IS_NULL = "isNull";
    public static final String IS_EMPTY = "isEmpty";
    public static final String IS_BLANK = "isBlank";
    public static final String IS_TRUE = "isTrue";
    public static final String IS_FALSE = "isFalse";
    public static final String EQUAL_TO_IGNORING_CASE = "isEqualToIgnoringCase";
    public static final String EQUALS = "equals";
    public static final String EQUALS_IGNORE_CASE = "equalsIgnoreCase";
    public static final String CONTAINS = "contains";
    public static final String CONTAINS_IGNORING_CASE = "containsIgnoringCase";
    public static final String MEDIA_TYPE = "isMediaType";
    public static final String STARTS_WITH = "startsWith";
    public static final String ENDS_WITH = "endsWith";
    public static final String MATCHES = "matches";

    public static final String HAS_SIZE = "hasSize";
    public static final String HAS_SIZE_GREATER_THAN = "hasSizeGreaterThan";
    public static final String HAS_SIZE_BETWEEN = "hasSizeBetween";
    public static final String DOES_NOT_HAVE_DUPLICATES = "doesNotHaveDuplicates";
    public static final String SORTED = "isSorted";
    public static final String SORTED_DESCENDING = "isSortedDescending";

    public static final String CONTAINS_KEY = "containsKey";
    public static final String CONTAINS_ONLY = "containsOnly";
    public static final String CONTAINS_ANY_OF = "containsAnyOf";
    public static final String CONTAINS_EXACTLY = "containsExactly";
    public static final String CONTAINS_EXACTLY_IN_ANY_ORDER = "containsExactlyInAnyOrder";
    public static final String ONE_OF = "isOneOf";
    public static final String ARG_IGNORE = "ignore";

    public static final String BETWEEN = "isBetween";
    public static final String CLOSE_TO = "isCloseTo";
    public static final String ARG_MIN = "min";
    public static final String ARG_MAX = "max";
    public static final String ARG_OFFSET = "offset";

    public static final String EQUAL_TO = "isEqualTo";
    public static final String GREATER_THAN = "isGreaterThan";
    public static final String LESS_THAN = "isLessThan";
    public static final String GREATER_THAN_OR_EQUAL_TO = "isGreaterThanOrEqualTo";
    public static final String LESS_THAN_OR_EQUAL_TO = "isLessThanOrEqualTo";

    public static final String SYMBOL_EQUAL_TO = "=";
    public static final String SYMBOL_GREATER_THAN_OR_EQUAL_TO = ">=";
    public static final String SYMBOL_LESS_THAN_OR_EQUAL_TO = "<=";
    public static final String SYMBOL_GREATER_THAN = ">";
    public static final String SYMBOL_LESS_THAN = "<";

    public static final String BEFORE = "isBefore";
    public static final String AFTER = "isAfter";
    public static final String EQUAL = "isEqual";
    public static final String PAST = "isPast";
    public static final String FUTURE = "isFuture";

    public static final String IS_PREFIX = "is";

    public static final Pattern NEGATION_PATTERN = Pattern.compile("^(not|!)(.+)");
}

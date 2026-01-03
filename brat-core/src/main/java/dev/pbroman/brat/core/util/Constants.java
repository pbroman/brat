package dev.pbroman.brat.core.util;

import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Constants {

    private Constants() {
        // utility class
    }

    public static final String PROTOCOL_LOGGER = "simple-api-tester-protocol";
    public static final String VALIDATION_LOGGER = "simple-api-tester-validation";

    public static final String CONSTANTS = "constants";
    public static final String HEADERS = "headers";
    public static final String ENV = "env";
    public static final String VARS = "vars";
    public static final String MISC = "misc";
    public static final String VALIDATIONS = "validations";
    public static final String REQUEST_RESULTS = "requestResults";
    public static final String RESPONSE = "response";
    public static final String RESPONSE_VARS = "responseVars";
    public static final String STATUS_CODE = "statusCode";
    public static final String BODY = "body";
    public static final String RAW_BODY = "raw";
    public static final String FILE_BODY = "file";
    public static final String BODY_STRING = "_bodyString";
    public static final String JSON = "json";
    public static final String VARIABLE_GROUP_NAME = "variableGroup";
    public static final String RESPONSE_HEADER_SHORTHAND = "rh";
    public static final String RESPONSE_STATUS_SHORTHAND = "sc";
    public static final String RESPONSE_BODY_SHORTHAND = "rb";
    public static final String RESPONSE_JSON_SHORTHAND = "rj";

    public static final Map<String, String> RESPONSE_SHORTHAND = Map.of(
            STATUS_CODE, RESPONSE_STATUS_SHORTHAND,
            BODY, RESPONSE_BODY_SHORTHAND,
            JSON, RESPONSE_JSON_SHORTHAND
    );

    public static final String NUMBER_CONDITION = "Number";
    public static final String STRING_CONDITION = "String";
    public static final String BOOLEAN_CONDITION = "Boolean";
    public static final String NULL_CONDITION = "Null";
    public static final String DATE_CONDITION = "Date";
    public static final String TIME_CONDITION = "Time";
    public static final String DATETIME_CONDITION = "DateTime";

    public static final String DEFAULT_TIMEOUT_MS = "30000";

    public static final Integer DEFAULT_MAX_ATTEMPTS = 3;

    public static final String PATH_DELIMITER = " |o| ";

    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String AUTH_TYPE_BEARER = "bearer";
    public static final String AUTH_TYPE_APIKEY = "apikey";
    public static final String AUTH_TYPE_NONE = "none";

    // Condition constants
    public static final String NULL = "null";
    public static final String EMPTY = "empty";
    public static final String BLANK = "blank";
    public static final String EQUALS = "equals";
    public static final String EQUALS_IGNORE_CASE = "equalsignorecase";
    public static final String CONTAINS = "contains";
    public static final String STARTS_WITH = "startswith";
    public static final String ENDS_WITH = "endswith";
    public static final String MATCHES = "matches";

    public static final String EQUAL_TO = "=";
    public static final String GREATER_THAN_OR_EQUAL = ">=";
    public static final String LESS_THAN_OR_EQUAL = "<=";
    public static final String GREATER_THAN = ">";
    public static final String LESS_THAN = "<";

    public static final String BEFORE = "before";
    public static final String AFTER = "after";
    public static final String EQUAL = "equal";
    public static final String PAST = "past";
    public static final String FUTURE = "future";

    public static final String IS_PREFIX = "is";

    public static final String NEGATION_REGEX = "^(not|!)(.+)";
    public static final Pattern NEGATION_PATTERN = Pattern.compile(NEGATION_REGEX);

    public static final List<String> SINGLE_VALUE_OPERATIONS = List.of(NULL, EMPTY, BLANK, TRUE, FALSE);


    // COLORS
    public static final String COLOR_RESET = "\033[0m";
    public static final String COLOR_RED = "\033[0;31m";
    public static final String COLOR_GREEN = "\033[0;32m";
    public static final String COLOR_YELLOW = "\033[0;33m";
    public static final String COLOR_BLUE = "\033[0;34m";
    public static final String COLOR_PURPLE = "\033[0;35m";
    public static final String COLOR_CYAN = "\033[0;36m";
    public static final String COLOR_WHITE = "\033[0;37m";
    public static final String COLOR_BLACK_BOLD = "\033[1;30m";

}

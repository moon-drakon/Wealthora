package com.spendwise.repository.cloud;

import com.spendwise.auth.AuthException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonSupport {

    private JsonSupport() {
    }

    static Object parse(String json) {
        Parser parser = new Parser(json == null ? "" : json);
        Object value = parser.value();
        parser.space();
        if (!parser.end()) throw invalid();
        return value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Character
                || value instanceof Enum<?> || value instanceof java.time.temporal.Temporal) {
            return quote(value.toString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) result.append(',');
                result.append(quote(String.valueOf(entry.getKey())))
                        .append(':').append(stringify(entry.getValue()));
                comma = true;
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder result = new StringBuilder("[");
            boolean comma = false;
            for (Object item : values) {
                if (comma) result.append(',');
                result.append(stringify(item));
                comma = true;
            }
            return result.append(']').toString();
        }
        throw new AuthException("Unsupported cloud finance request value.");
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static AuthException invalid() {
        return new AuthException(
                "The cloud finance server returned invalid JSON.");
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object value() {
            space();
            if (end()) throw invalid();
            return switch (text.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            index++;
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            while (true) {
                space();
                if (end() || text.charAt(index) != '"') throw invalid();
                String key = string();
                space();
                if (!take(':')) throw invalid();
                result.put(key, value());
                space();
                if (take('}')) return result;
                if (!take(',')) throw invalid();
            }
        }

        private List<Object> array() {
            index++;
            ArrayList<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                space();
                if (take(']')) return result;
                if (!take(',')) throw invalid();
            }
        }

        private String string() {
            index++;
            StringBuilder result = new StringBuilder();
            while (!end()) {
                char character = text.charAt(index++);
                if (character == '"') return result.toString();
                if (character != '\\') {
                    result.append(character);
                    continue;
                }
                if (end()) throw invalid();
                char escaped = text.charAt(index++);
                result.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b'; case 'f' -> '\f';
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw invalid();
                });
            }
            throw invalid();
        }

        private char unicode() {
            if (index + 4 > text.length()) throw invalid();
            try {
                char value = (char) Integer.parseInt(
                        text.substring(index, index + 4), 16);
                index += 4;
                return value;
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, index)) throw invalid();
            index += expected.length();
            return value;
        }

        private BigDecimal number() {
            int start = index;
            if (take('-') && end()) throw invalid();
            while (!end() && Character.isDigit(text.charAt(index))) index++;
            if (take('.')) {
                while (!end() && Character.isDigit(text.charAt(index))) index++;
            }
            if (!end() && (text.charAt(index) == 'e'
                    || text.charAt(index) == 'E')) {
                index++;
                if (!end() && (text.charAt(index) == '+'
                        || text.charAt(index) == '-')) index++;
                while (!end() && Character.isDigit(text.charAt(index))) index++;
            }
            try {
                return new BigDecimal(text.substring(start, index));
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private boolean take(char expected) {
            if (!end() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void space() {
            while (!end() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private boolean end() {
            return index >= text.length();
        }
    }
}

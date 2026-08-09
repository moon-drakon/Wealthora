package com.wealthora.otp.relay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JsonSupport {

    private JsonSupport() {
    }

    static Map<String, Object> parseObject(String source) {
        return new Parser(source).object();
    }

    static void requireExactKeys(Map<String, Object> values, String... names) {
        if (!values.keySet().equals(Set.of(names))) {
            throw new InvalidRequestException("Unexpected JSON fields.");
        }
    }

    static String text(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String text)) {
            throw new InvalidRequestException(name + " must be text.");
        }
        return text;
    }

    static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        throw new IllegalArgumentException(
                                "JSON text contains a control character.");
                    }
                    escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static final class Parser {
        private final String source;
        private int position;

        Parser(String source) {
            this.source = Objects.requireNonNull(source);
        }

        Map<String, Object> object() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            whitespace();
            take('{');
            whitespace();
            if (peek('}')) {
                position++;
                finish();
                return values;
            }
            while (true) {
                String name = string();
                whitespace();
                take(':');
                whitespace();
                Object prior = values.putIfAbsent(name, string());
                if (prior != null) {
                    throw invalid();
                }
                whitespace();
                if (peek('}')) {
                    position++;
                    finish();
                    return values;
                }
                take(',');
                whitespace();
            }
        }

        private String string() {
            take('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char character = source.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (position >= source.length()) {
                        throw invalid();
                    }
                    value.append(switch (source.charAt(position++)) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw invalid();
                    });
                } else if (character < 0x20) {
                    throw invalid();
                } else {
                    value.append(character);
                }
            }
            throw invalid();
        }

        private void take(char expected) {
            if (!peek(expected)) {
                throw invalid();
            }
            position++;
        }

        private boolean peek(char expected) {
            return position < source.length()
                    && source.charAt(position) == expected;
        }

        private void whitespace() {
            while (position < source.length()
                    && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void finish() {
            whitespace();
            if (position != source.length()) {
                throw invalid();
            }
        }

        private InvalidRequestException invalid() {
            return new InvalidRequestException("Request JSON is invalid.");
        }
    }
}

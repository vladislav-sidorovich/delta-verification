/*
 * MIT License
 *
 * Copyright (c) 2026 Vladislav Sidorovich
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package io.github.vladislavsidorovich.deltaverification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency parser for {@code delta-verification/index.json}.
 */
final class JsonIndexParser {

    private final String text;
    private int pos;

    private JsonIndexParser(String text) {
        this.text = text;
        this.pos = 0;
    }

    @SuppressWarnings("unchecked")
    static List<DeltaTableInfo> parseTables(String jsonText) {
        JsonIndexParser parser = new JsonIndexParser(jsonText);
        Object rootObj = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != jsonText.length() || !(rootObj instanceof Map)) {
            throw new IllegalStateException("Malformed index.json root");
        }
        Map<String, Object> root = (Map<String, Object>) rootObj;
        Object rawTables = root.get("tables");
        if (!(rawTables instanceof List)) {
            throw new IllegalStateException("Missing 'tables' array in index.json");
        }
        List<Object> tableItems = (List<Object>) rawTables;
        List<DeltaTableInfo> result = new ArrayList<DeltaTableInfo>(tableItems.size());
        for (Object item : tableItems) {
            Map<String, Object> m = (Map<String, Object>) item;
            result.add(new DeltaTableInfo(
                    (String) m.get("name"),
                    (String) m.get("resourcePath"),
                    Boolean.TRUE.equals(m.get("hasDeltaLog")),
                    toInteger(m.get("latestVersion")),
                    Boolean.TRUE.equals(m.get("hasCheckpoint")),
                    toInteger(m.get("minReaderVersion")),
                    toInteger(m.get("minWriterVersion")),
                    toStringList(m.get("readerFeatures")),
                    toStringList(m.get("writerFeatures")),
                    (String) m.get("notice"),
                    toStringList(m.get("files"))));
        }
        return Collections.unmodifiableList(result);
    }

    private static Integer toInteger(Object val) {
        return val instanceof Number ? Integer.valueOf(((Number) val).intValue()) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object val) {
        if (!(val instanceof List)) {
            return Collections.emptyList();
        }
        List<Object> raw = (List<Object>) val;
        List<String> out = new ArrayList<String>(raw.size());
        for (Object elem : raw) {
            out.add((String) elem);
        }
        return out;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new IllegalStateException("Unexpected end of JSON");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw new IllegalStateException("Unexpected character '" + c + "' at offset " + pos);
        }
    }

    private Map<String, Object> parseObject() {
        expectChar('{');
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expectChar(':');
            Object val = parseValue();
            map.put(key, val);
            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else if (peek('}')) {
                pos++;
                return map;
            } else {
                throw new IllegalStateException("Expected ',' or '}' at offset " + pos);
            }
        }
    }

    private List<Object> parseArray() {
        expectChar('[');
        List<Object> list = new ArrayList<Object>();
        skipWhitespace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else if (peek(']')) {
                pos++;
                return list;
            } else {
                throw new IllegalStateException("Expected ',' or ']' at offset " + pos);
            }
        }
    }

    private String parseString() {
        expectChar('"');
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw new IllegalStateException("Unterminated escape in JSON string");
                }
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(esc);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new IllegalStateException("Invalid unicode escape at offset " + pos);
                        }
                        int code = Integer.parseInt(text.substring(pos, pos + 4), 16);
                        sb.append((char) code);
                        pos += 4;
                        break;
                    default:
                        throw new IllegalStateException("Invalid escape '\\" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalStateException("Unterminated JSON string");
    }

    private Number parseNumber() {
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
            pos++;
        }
        boolean isDecimal = false;
        if (pos < text.length() && text.charAt(pos) == '.') {
            isDecimal = true;
            pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            isDecimal = true;
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        String num = text.substring(start, pos);
        return isDecimal ? (Number) Double.valueOf(num) : (Number) Long.valueOf(num);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private boolean peek(char expected) {
        return pos < text.length() && text.charAt(pos) == expected;
    }

    private void expectChar(char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw new IllegalStateException("Expected '" + expected + "' at offset " + pos);
        }
        pos++;
    }

    private void expectLiteral(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw new IllegalStateException("Expected '" + literal + "' at offset " + pos);
        }
        pos += literal.length();
    }
}

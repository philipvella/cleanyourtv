package io.github.philipvella.vellaradio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for the station directory's answers and the stations kept on the TV: objects become Maps, arrays
 * Lists, numbers Doubles, plus Strings, Booleans and null. Plain Java, so it runs in JVM tests (android.jar's org.json
 * is only a stub there).
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.space();
        Object v = p.value();
        p.space();
        if (p.i != p.s.length()) throw p.bad("trailing text");
        return v;
    }

    /** A JSON string literal for the text, quotes included. */
    static String quote(String text) {
        StringBuilder b = new StringBuilder(text.length() + 2).append('"');
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private Object value() {
        if (i >= s.length()) throw bad("end of text");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        space();
        if (peek('}')) return m;
        while (true) {
            space();
            if (i >= s.length() || s.charAt(i) != '"') throw bad("a key");
            String key = string();
            space();
            expect(':');
            space();
            m.put(key, value());
            space();
            if (peek('}')) return m;
            expect(',');
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        space();
        if (peek(']')) return l;
        while (true) {
            space();
            l.add(value());
            space();
            if (peek(']')) return l;
            expect(',');
        }
    }

    private String string() {
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw bad("end of string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) throw bad("escape");
            char e = s.charAt(i++);
            switch (e) {
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw bad("escape");
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException no) {
                        throw bad("escape");
                    }
                    i += 4;
                    break;
                default: b.append(e);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw bad("a value");
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException no) {
            throw bad("a number");
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) throw bad(w);
        i += w.length();
        return v;
    }

    private boolean peek(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw bad("'" + c + "'");
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException bad(String wanted) {
        return new IllegalArgumentException("JSON: expected " + wanted + " at " + i);
    }
}

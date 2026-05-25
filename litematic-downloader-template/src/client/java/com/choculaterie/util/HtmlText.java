package com.choculaterie.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;

public final class HtmlText {

    private HtmlText() {}

    private static final Map<String, String> NAMED_ENTITIES = new HashMap<>();

    static {
        NAMED_ENTITIES.put("amp", "&");
        NAMED_ENTITIES.put("lt", "<");
        NAMED_ENTITIES.put("gt", ">");
        NAMED_ENTITIES.put("quot", "\"");
        NAMED_ENTITIES.put("apos", "'");
        NAMED_ENTITIES.put("nbsp", " ");
        NAMED_ENTITIES.put("hellip", "…");
        NAMED_ENTITIES.put("mdash", "—");
        NAMED_ENTITIES.put("ndash", "–");
        NAMED_ENTITIES.put("lsquo", "‘");
        NAMED_ENTITIES.put("rsquo", "’");
        NAMED_ENTITIES.put("ldquo", "“");
        NAMED_ENTITIES.put("rdquo", "”");
        NAMED_ENTITIES.put("copy", "©");
        NAMED_ENTITIES.put("reg", "®");
        NAMED_ENTITIES.put("trade", "™");
        NAMED_ENTITIES.put("deg", "°");
    }

    public static Component toComponent(String html) {
        if (html == null || html.isEmpty()) {
            return Component.empty();
        }

        Builder builder = new Builder();
        int bold = 0;
        int italic = 0;
        int underline = 0;
        int i = 0;
        int n = html.length();

        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                int close = html.indexOf('>', i);
                if (close < 0) {
                    break;
                }
                String raw = html.substring(i + 1, close).trim();
                i = close + 1;
                if (raw.isEmpty() || raw.startsWith("!")) {
                    continue;
                }
                boolean closing = raw.startsWith("/");
                if (closing) {
                    raw = raw.substring(1).trim();
                }
                switch (tagName(raw)) {
                    case "br" -> builder.lineBreak(1);
                    case "p", "div", "ul", "ol", "blockquote", "section", "article",
                         "header", "footer", "table", "tr", "hr" -> builder.lineBreak(2);
                    case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        if (closing) {
                            bold = Math.max(0, bold - 1);
                            builder.setStyle(bold, italic, underline);
                            builder.lineBreak(2);
                        } else {
                            builder.lineBreak(2);
                            bold++;
                            builder.setStyle(bold, italic, underline);
                        }
                    }
                    case "li" -> {
                        builder.lineBreak(1);
                        if (!closing) {
                            builder.append("• ");
                        }
                    }
                    case "b", "strong" -> {
                        bold = closing ? Math.max(0, bold - 1) : bold + 1;
                        builder.setStyle(bold, italic, underline);
                    }
                    case "i", "em" -> {
                        italic = closing ? Math.max(0, italic - 1) : italic + 1;
                        builder.setStyle(bold, italic, underline);
                    }
                    case "u", "ins" -> {
                        underline = closing ? Math.max(0, underline - 1) : underline + 1;
                        builder.setStyle(bold, italic, underline);
                    }
                    default -> { }
                }
            } else {
                int next = html.indexOf('<', i);
                if (next < 0) {
                    next = n;
                }
                builder.append(decodeEntities(html.substring(i, next)));
                i = next;
            }
        }
        return builder.build();
    }

    private static String tagName(String raw) {
        int end = 0;
        while (end < raw.length() && Character.isLetterOrDigit(raw.charAt(end))) {
            end++;
        }
        return raw.substring(0, end).toLowerCase();
    }

    private static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i && semi - i <= 12) {
                    String replacement = resolveEntity(s.substring(i + 1, semi));
                    if (replacement != null) {
                        out.append(replacement);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String resolveEntity(String body) {
        if (body.isEmpty()) {
            return null;
        }
        if (body.charAt(0) == '#') {
            try {
                int code = (body.length() > 2 && (body.charAt(1) == 'x' || body.charAt(1) == 'X'))
                        ? Integer.parseInt(body.substring(2), 16)
                        : Integer.parseInt(body.substring(1));
                if (code <= 0 || code > 0x10FFFF) {
                    return null;
                }
                return new String(Character.toChars(code));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return NAMED_ENTITIES.get(body.toLowerCase());
    }

    private static final class Builder {
        private final MutableComponent root = Component.empty();
        private final StringBuilder run = new StringBuilder();
        private Style style = Style.EMPTY;
        private int pendingNewlines = 0;
        private boolean pendingSpace = false;
        private boolean hasContent = false;

        void setStyle(int bold, int italic, int underline) {
            Style next = Style.EMPTY;
            if (bold > 0) {
                next = next.withBold(true);
            }
            if (italic > 0) {
                next = next.withItalic(true);
            }
            if (underline > 0) {
                next = next.withUnderlined(true);
            }
            if (!next.equals(style)) {
                flush();
                style = next;
            }
        }

        void lineBreak(int count) {
            if (count > pendingNewlines) {
                pendingNewlines = Math.min(2, count);
            }
        }

        void append(String text) {
            if (text.isEmpty()) {
                return;
            }
            String collapsed = text.replaceAll("[\\s\\u00a0]+", " ");
            if (collapsed.equals(" ")) {
                if (hasContent && pendingNewlines == 0) {
                    pendingSpace = true;
                }
                return;
            }

            boolean leadingSpace = collapsed.startsWith(" ");
            boolean trailingSpace = collapsed.endsWith(" ");
            String core = collapsed.trim();
            if (core.isEmpty()) {
                return;
            }

            if (hasContent && pendingNewlines > 0) {
                for (int k = 0; k < pendingNewlines; k++) {
                    run.append('\n');
                }
            } else if (hasContent && (pendingSpace || leadingSpace)) {
                run.append(' ');
            }
            pendingNewlines = 0;
            pendingSpace = trailingSpace;
            run.append(core);
            hasContent = true;
        }

        void flush() {
            if (run.length() > 0) {
                root.append(Component.literal(run.toString()).setStyle(style));
                run.setLength(0);
            }
        }

        Component build() {
            flush();
            return root;
        }
    }
}

package com.nearmeet.common.text;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class TextSanitizer {

    private TextSanitizer() {
    }

    /** trim + collapse runs of whitespace to one space */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    /** strips every HTML tag so nothing can be stored and replayed as script (stored XSS) */
    public static String plainText(String value) {
        if (value == null) {
            return null;
        }
        String noHtml = Jsoup.clean(value, Safelist.none());
        return clean(org.jsoup.parser.Parser.unescapeEntities(noHtml, false));
    }
}

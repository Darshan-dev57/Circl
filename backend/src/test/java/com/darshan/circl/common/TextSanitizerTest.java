package com.darshan.circl.common;

import com.darshan.circl.common.text.TextSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

    @Test
    void stripsTagsAndScripts() {
        assertThat(TextSanitizer.plainText("<b>hi</b> <script>alert('x')</script>there"))
                .isEqualTo("hi there");
    }

    @Test
    void keepsNormalPunctuation() {
        assertThat(TextSanitizer.plainText("Bring water & shoes, 5 > 3")).isEqualTo("Bring water & shoes, 5 > 3");
    }

    @Test
    void collapsesSpaces() {
        assertThat(TextSanitizer.clean("  a   b \n c ")).isEqualTo("a b c");
    }
}

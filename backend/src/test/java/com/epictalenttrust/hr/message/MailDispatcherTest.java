package com.epictalenttrust.hr.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailDispatcherTest {

    @Test
    void acceptsOnlyPlainHexColours() {
        assertThat(MailDispatcher.hexOr(" #1A2b3C ", "#000000")).isEqualTo("#1A2b3C");
        assertThat(MailDispatcher.hexOr("red", "#000000")).isEqualTo("#000000");
        assertThat(MailDispatcher.hexOr("#123456;background:url(x)", "#000000")).isEqualTo("#000000");
        assertThat(MailDispatcher.hexOr(null, "#000000")).isEqualTo("#000000");
    }
}

package cowork.llm;

// AnthropicClientRedactTest — anything shaped like an API key is masked in
// error strings; ordinary text and nulls pass through.

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientRedactTest {

    @Test
    void masksAnthropicStyleKeyInsideAnErrorString() {
        String raw = "[Claude ERROR 401] {\"error\":\"invalid x-api-key sk-ant-abcdefghijklmnop\"}";
        String out = AnthropicClient.redact(raw);
        assertFalse(out.contains("sk-ant-abcdefghijklmnop"));
        assertTrue(out.contains("sk-***"));
        assertTrue(out.startsWith("[Claude ERROR 401] {\"error\":\"invalid x-api-key "));
    }

    @Test
    void masksEveryOccurrenceAndKeepsSurroundingText() {
        String out = AnthropicClient.redact("a sk-ant-abcdefghijklmnop b sk-proj_XYZ12345678 c");
        assertEquals("a sk-*** b sk-*** c", out);
    }

    @Test
    void leavesOrdinaryTextAndNullAlone() {
        assertEquals("rate limited, retry later", AnthropicClient.redact("rate limited, retry later"));
        assertEquals("sk-short", AnthropicClient.redact("sk-short"));
        assertNull(AnthropicClient.redact(null));
    }
}

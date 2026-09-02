package cowork.workflows;

import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EmailDraftTool renders a reviewable draft and never reports an error. */
class EmailDraftToolTest {

    @Test
    void draftContainsSubjectAndRecipient() {
        EmailDraftTool tool = new EmailDraftTool();
        ToolCall call = new ToolCall("call-1", "draft_email", Map.of(
            "to", "rso-office@uic.edu",
            "cc", "treasurer@example.org",
            "subject", "Room request: SCE 302 on 2026-10-01",
            "body", "Hello,\n\nPlease confirm the reservation."));

        ToolResult result = tool.handler().apply(call);

        assertFalse(result.isError());
        assertEquals("call-1", result.callId());
        assertTrue(result.content().contains("Subject: Room request: SCE 302 on 2026-10-01"));
        assertTrue(result.content().contains("To: rso-office@uic.edu"));
        assertTrue(result.content().contains("Cc: treasurer@example.org"));
        assertTrue(result.content().contains("not yet sent"));
        assertEquals("draft_email", tool.schema().name());
    }
}

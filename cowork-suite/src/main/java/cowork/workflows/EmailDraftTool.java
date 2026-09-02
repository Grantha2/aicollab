package cowork.workflows;

import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import cowork.llm.ToolSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * "draft_email" tool: assembles the arguments into a plain-text email draft and returns it
 * as text. It never sends; an agent must not press Send on the user's behalf, so the user
 * copies the draft into their own mail client.
 */
public final class EmailDraftTool {

    public ToolSchema schema() {
        return ToolSchema.stringParams(
                "draft_email",
                "Compose a reviewable plain-text email draft. The user "
                        + "will approve and send from their own client.",
                List.of("to", "subject", "body"),
                Map.of(
                        "to", "Primary recipient email address.",
                        "subject", "Email subject line.",
                        "body", "Email body (plain text)."
                ));
    }

    public Function<ToolCall, ToolResult> handler() {
        return call -> {
            String to = str(call.arguments().get("to"));
            String cc = str(call.arguments().get("cc"));
            String subject = str(call.arguments().get("subject"));
            String body = str(call.arguments().get("body"));

            StringBuilder draft = new StringBuilder();
            draft.append("----- DRAFT EMAIL (not yet sent) -----\n");
            draft.append("To: ").append(to).append("\n");
            if (!cc.isBlank()) draft.append("Cc: ").append(cc).append("\n");
            draft.append("Subject: ").append(subject).append("\n\n");
            draft.append(body).append("\n");
            draft.append("----- END DRAFT -----\n");
            draft.append("Review the draft above. The user will press Send "
                    + "in their mail client, or the workflow will ask for "
                    + "explicit authorization before dispatching.");
            return ToolResult.ok(call.id(), draft.toString());
        };
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}

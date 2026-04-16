package collab.workflows;

import collab.ToolCall;
import collab.ToolResult;
import collab.ToolSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

// ============================================================
// EmailDraftTool.java — Compose a reviewable email draft.
//
// WHAT THIS CLASS DOES (one sentence):
// Registers a ToolSchema named "draft_email" whose handler assembles
// the arguments into a nicely-formatted email and returns it as text
// (no network calls, no sending).
//
// WHY A DRAFT-ONLY TOOL:
// An AI agent should never press Send on the user's behalf without
// explicit confirmation. "Draft" means we hand the user the exact
// bytes that would be sent and leave the press-to-send decision to
// them. The actual send action, when we build it, will be a separate
// tool gated on a UI confirmation modal (see RoomReservationWorkflow).
//
// FORMAT:
// Returns a plain-text block that the user can copy into their mail
// client, or that a future SendEmailTool can slurp and dispatch over
// SMTP / Graph API / whatever. No HTML — we keep the draft portable.
// ============================================================
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

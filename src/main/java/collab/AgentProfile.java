package collab;

// ============================================================
// AgentProfile.java — Holds one AI agent's identity on the panel.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores the name, perspective, and detailed lens description for
// one AI agent (Claude, GPT, or Gemini) so it can be injected
// into prompts to give each model a distinct personality.
//
// HOW IT FITS THE ARCHITECTURE:
// PromptBuilder uses AgentProfile.toBriefing() to assemble the
// agent identity layer — the part that tells each model
// WHO it is and HOW it should think. Without this, all three
// models would respond as generic assistants giving nearly
// identical answers.
//
// CONTEXT LAYERING ARCHITECTURE (CLA):
//   Layer 3: WHO IS ASKING  (StakeholderProfile)
//   Layer 1: TEAM CONTEXT   (shared, lives in PromptBuilder)
//   Layer 2: THIS AGENT     (AgentProfile — this class)
// ============================================================

import java.util.List;

public class AgentProfile {

    // The model's display name — shown in console output and prompts.
    private String name;

    // The high-level role — e.g., "Architecture & Quality".
    // This is the one-line summary of what this agent focuses on.
    private String perspective;

    // The detailed multi-line description of priorities and style.
    // This is what actually shapes the model's behavior — it tells
    // the AI what to prioritize, how to disagree, and what to watch for.
    private String lens;

    AgentProfile() {}

    public AgentProfile(String name, String perspective, String lens) {
        this.name = name;
        this.perspective = perspective;
        this.lens = lens;
    }

    public String getName()        { return name; }
    public String getPerspective() { return perspective; }
    public String getLens() { return lens; }

    // ============================================================
    // toBriefing() — Formats this agent's identity as a prompt-ready
    // text block that gets prepended to every API call.
    //
    // This is the agent identity layer. When a model reads
    // this block, it knows: "I am Claude, I focus on Architecture
    // & Quality, and here's exactly how I should think."
    // ============================================================
    public String toBriefing() {
        return "=== YOUR AGENT IDENTITY ===\n"
             + "Agent name: " + name + "\n"
             + "Perspective: " + perspective + "\n"
             + lens + "\n\n";
    }

    // ============================================================
    // getDefaults() — Returns an empty list. Agent profiles are now
    // provided by the user during first-launch setup and stored in
    // the profile system for subsequent launches.
    // ============================================================
    public static List<AgentProfile> getDefaults() {
        return List.of();
    }
}

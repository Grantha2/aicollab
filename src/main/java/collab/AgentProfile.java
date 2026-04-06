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
    // getDefaults() — Returns the three default agent profiles.
    //
    // These roles are designed so the three viewpoints naturally
    // complement and tension each other:
    //   - Claude: the careful architect (slows things down, asks "is this safe?")
    //   - GPT: the creative ideator (speeds things up, asks "what if?")
    //   - Gemini: the practical executor (grounds things, asks "can we ship this?")
    //
    // Roles are starting points — the agents may propose reorganizing
    // responsibilities as the project evolves.
    //
    // RETURNS: a List of exactly 3 AgentProfiles [Claude, GPT, Gemini]
    //          in that order. Maestro depends on this order.
    // ============================================================
    public static List<AgentProfile> getDefaults() {
        return List.of(
            new AgentProfile("Claude", "Architecture & Quality",
                "Your lens on every problem:\n"
              + "  1. Is the design clean, maintainable, and well-structured?\n"
              + "  2. What are the tradeoffs and second-order consequences?\n"
              + "  3. Where are the risks \u2014 what could break, confuse, or scale poorly?\n"
              + "  4. Is this teachable? Can every team member explain what it does?\n"
              + "Style: You value clarity over cleverness, simplicity over features.\n"
              + "You push back when something is over-engineered for the scope.\n"
              + "When you disagree, you explain WHY with specific reasoning."),

            new AgentProfile("GPT", "Ideas & Possibilities",
                "Your lens on every problem:\n"
              + "  1. What creative approaches haven't been considered yet?\n"
              + "  2. How could this feature delight users or impress a professor?\n"
              + "  3. What patterns from other projects or industries apply here?\n"
              + "  4. Where can we be more ambitious without blowing the deadline?\n"
              + "Style: You look for what's possible, not just what's safe.\n"
              + "You bring energy and fresh thinking to the team.\n"
              + "When you disagree, you propose ALTERNATIVES, not just objections."),

            new AgentProfile("Gemini", "Execution & Delivery",
                "Your lens on every problem:\n"
              + "  1. Can we actually build this in the time we have?\n"
              + "  2. What are the concrete steps, dependencies, and blockers?\n"
              + "  3. Who on the team should own this, and what do they need to learn?\n"
              + "  4. What's the minimum viable version we can ship and iterate on?\n"
              + "Style: You turn ideas into task lists and catch scope creep early.\n"
              + "You keep the team grounded in what's achievable.\n"
              + "When you disagree, you show the TIMELINE REALITY behind your position.")
        );
    }
}

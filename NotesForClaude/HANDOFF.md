# AI Collaboration Platform — Handoff to Claude Code

## Project Summary

Four university students (Grant, Xavier, Lisiana, Arizbeth) are building a
Java-based multi-model AI collaboration platform as a final project. 5-week
deadline. Grant has built v0.3 solo; the other three are onboarding from break.

The platform runs a 3-phase debate cycle: Claude, GPT, and Gemini each respond
independently to a user's prompt, then react to each other's responses, then
Claude synthesizes everything into a structured report. Every API call carries
a three-layer "layered context" of context: team context, agent identity (each model has
a distinct perspective), and the active team member's profile.

## Current State (v0.3)

**One file: Main.java (~1,080 lines, zero external dependencies)**

Architecture:
- CLI loop with multi-line input (type SEND to submit)
- Hotseat system: 4 student profiles (Grant, Xavier, Lisiana, Arizbeth)
- 3 agent identities: Claude (Architecture & Quality), GPT (Ideas &
  Possibilities), Gemini (Execution & Delivery) — equal partners, no hierarchy
- 3-phase debate: independent responses → cross-reactions → synthesis
- Cost safeguards: confirmation before each cycle, post-cycle pause, counter
- Provider wrappers: callClaude, callOpenAi, callGemini with manual JSON
- Utility methods: escapeForJson, extractField (string-based JSON parsing)

Key methods:
- main() — CLI loop with hotseat selection, multi-line input, safeguards
- selectStakeholder() — profile selection menu
- formatStakeholderBriefing() — converts active profile to prompt context
- buildAgentPrompt() — assembles the full layered context (team + agent + member + prompt)
- runDebateCycle() — orchestrates all 3 phases
- buildReactionPrompt() — Phase 2 prompt with agent identity + peer responses
- buildSynthesisPrompt() — Phase 3 prompt with all 6 outputs + member context
- callClaude/callOpenAi/callGemini — provider-specific HTTP + JSON wrappers
- escapeForJson() — sanitizes input for JSON embedding
- extractField() — lightweight string-based JSON value extraction

API config:
- Claude: api.anthropic.com, claude-sonnet-4-20250514, x-api-key header
- GPT: api.openai.com, gpt-4o, Bearer token, max_completion_tokens (not max_tokens)
- Gemini: generativelanguage.googleapis.com, gemini-2.5-flash, key in URL

Known issues:
- extractField() uses lastIndexOf + manual quote parsing — brittle
- API keys hardcoded as constants (need externalization)
- No conversation memory across cycles
- No persistence (sessions lost on exit)
- Single-threaded sequential API calls (7 calls per cycle, slow)

## First Group Debate Results (ConversationPanel1.txt)

The three AI agents debated the project's roadmap. Key consensus and tensions:

### All Three Agents Agreed On:
1. Refactoring Main.java is non-negotiable — it's a collaboration bottleneck
2. Manual JSON parsing (extractField) is a critical risk that will break
3. Multi-user collaboration must be scoped WAY down (session handoff, not
   real-time WebSockets)
4. Conversation persistence + simple GUI are the MVP features
5. Prompt engineering (the layered context model) is the project's strategic asset

### Key Disagreements:
- INCREMENTAL vs BIG-BANG refactor: Claude says extract classes one at a time
  to maintain Grant's architectural control. Gemini says do a full Maven +
  Jackson + modular refactor in weeks 1-2.
- DATABASE vs FILE persistence: Claude says serialize to JSON files (no
  learning curve). Gemini says SQLite/H2 with JDBC (more robust but more to
  learn).
- AMBITION level: GPT pushed WebSockets, knowledge bases, animations. Both
  Claude and Gemini called this scope creep that guarantees failure.

### Critical Insight That Emerged:
The "learning curve tax" — requiring the team to learn Maven + Jackson + JDBC +
JavaFX simultaneously while building features is the biggest execution risk.
Grant's "rusty Java" constraint multiplies this. The panel recommended
minimizing new technology adoption and maximizing what can be done with Java 21
built-ins.

## Team Status

- Grant: Project lead, built v0.1-v0.3 solo, strongest codebase understanding,
  rusty Java but learning fast, owns architecture and prompt engineering
- Xavier: Not yet active, onboarding from break, role TBD
- Lisiana: Not yet active, onboarding from break, role TBD
- Arizbeth: Not yet active, onboarding from break, role TBD

## 5-Week Roadmap

### Week 1: Foundation (everyone learns together)
- All 4 members run Main.java locally with live API keys
- Git repo live with .gitignore for keys
- Everyone reads code comments, breaks things to learn
- Externalize API keys to config.properties
- Team code review: walk through Main.java line by line

### Week 2: Organization Context + Conversation Memory
- Add Organization fields (mission, industry, constraints) to context
- Build ConversationContext class (ArrayList of prompt/response pairs)
- Wire prior cycle context into Phase 1 prompts for memory across cycles

### Week 3: Multi-Stakeholder Input + Structured Output
- Multiple team members contribute prompts before triggering a cycle
- Models output structured reactions (stance, target, rationale)
- Test with 2-3 members prompting the same cycle

### Week 4: Simple GUI + Session Persistence
- Java Swing window: stakeholder selector, three model panes, synthesis panel
- Save completed cycles to disk (JSON or text files)
- Load previous session option

### Week 5: Polish, Testing, Presentation
- Graceful degradation (continue with 2 models if one fails)
- Retry logic with backoff for rate limits
- README.md, demo script, practice presentation

## Future Vision (post-submission)
- Proprietary orchestration model replacing Claude as synthesizer
- Web-based interface for real multi-user access
- Async stakeholder input
- Dynamic debate rounds (maestro decides if more rounds needed)
- Model-agnostic plugin system
- Bias detection in synthesis phase
- Cost dashboard

## Files Available

- Main.java — the complete working platform (v0.3 with agent profiles,
  student profiles, multi-line input, cost safeguards)
- ROADMAP.md — full project roadmap with weekly tasks and owners
- ConversationPanel1.txt — first real 3-model debate output

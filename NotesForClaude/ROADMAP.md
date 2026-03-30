# AI Collaboration Platform — Project Brief & Roadmap

## Updated Conversation Starter (paste this into the platform)

We are a team of four university students building a Java-based multi-model AI
collaboration platform as our final project, due in 5 weeks. Here is where we
stand and where we're headed.

WHAT WE'VE BUILT (v0.3, working today):
A single Main.java (~1,000 lines, zero external dependencies) that runs a
complete 3-phase debate cycle using Java 21's built-in HttpClient. The user
selects a stakeholder profile from a hotseat menu (CEO, CTO, CFO, or VP
Product) — each with name, title, KPIs, decision authority, and background.
Every API call carries a three-layer "onion" of context: (1) team context
describing the advisory panel, (2) an agent identity giving each model a
distinct role — Claude as Strategy & Risk Analyst, GPT as Innovation &
Opportunity Analyst, Gemini as Technical Feasibility Lead — and (3) the active
stakeholder's full profile so the panel knows who they're advising. Phase 1
sends the stakeholder's prompt independently to all three models. Phase 2
passes each model the other two responses and asks it to react from its
assigned perspective — challenging, refining, or agreeing. Phase 3 sends all
six outputs to Claude as orchestrator, which synthesizes a structured report
covering areas of agreement, disagreement, key insights, and a
stakeholder-specific recommendation. Cost safeguards include a confirmation
step before every cycle, a post-cycle pause with running API call counts, and
a session tracker. The user can switch stakeholder profiles between cycles
without restarting. Provider integrations are thin wrappers: callClaude,
callOpenAi, callGemini each build provider-specific JSON and headers, with
escapeForJson for input sanitization and extractField for lightweight
string-based response parsing.

WHAT WE'RE BUILDING NEXT (see roadmap below):
Collaborative multi-user prompting where multiple stakeholders contribute to
the same session. Rich organizational context (company mission, industry,
constraints) prepended to every call. Conversation persistence so the panel
retains context across cycles. A simple GUI so users can visualize each phase
of the debate. Eventually, a proprietary orchestration model to replace
Claude as synthesizer.

Given this context — what we've built, what we're building, and our 5-week
deadline — what are the most critical next steps, technical risks, and
feature priorities we should focus on to deliver a strong, defensible final
project?


## Current Solution Summary

The platform is a CLI loop in one Java file. It reads a user prompt, blocks
empty input, supports `quit` and `switch` commands, and requires explicit
`y/n` confirmation before running a paid cycle (7 API calls per debate). Each
cycle runs three phases: independent responses, cross-reactions where each
model reviews the other two from its assigned agent perspective, and a final
synthesis where Claude merges all six prior outputs into a stakeholder-tailored
report. The design is entirely prompt-driven — orchestration happens by passing
progressively richer text prompts between model calls, not through any special
debate API. Provider integrations are thin HTTP wrappers with manual JSON
construction and string-based response extraction. Main tradeoff: simple and
teachable, but brittle for production (hardcoded config, manual JSON parsing,
no persistence).


## 5-Week Roadmap

### WEEK 1 — Foundation Hardening (current week)
Owner: Full team together

GOALS:
- All four team members can run Main.java locally with live API keys
- Everyone understands the 3-phase flow by reading the comments
- Git repo is live with branch protection and a .gitignore for keys

TASKS:
- [ ] Create GitHub repo, push Main.java (keys in .gitignore)
- [ ] Create a config.properties file for API keys (read at startup)
  - Replaces hardcoded keys — first real refactor the team does together
  - Teaches file I/O and Properties class
- [ ] Each team member runs a full debate cycle and reads every comment
- [ ] Break things on purpose: remove a header, swap a model name, corrupt
      the JSON — learn what each piece does by watching it fail
- [ ] Team code review: walk through Main.java together line by line

DELIVERABLE: Working platform on all 4 machines, keys externalized, everyone
can explain the HTTP request → JSON → response flow.

---

### WEEK 2 — Organization Context & Conversation Memory
Owner: Person A (org context) + Person D (conversation memory)

GOALS:
- Organization-level context (mission, industry, constraints) prepended to
  every API call alongside stakeholder profiles
- Conversation history persists across debate cycles within a session

TASKS:
- [ ] Person A: Add Organization fields (name, industry, mission, size,
      constraints) as constants or loaded from a file. Wire into
      formatStakeholderBriefing() so every call includes org context.
- [ ] Person D: Create a ConversationContext class (ArrayList of prompt/
      response pairs tagged by author and phase). Pass prior context into
      Phase 1 prompts so the panel has memory of earlier cycles.
- [ ] Both: Write the toBriefing() methods that convert these objects into
      plain-English blocks for the prompt
- [ ] Team integration: merge both into Main.java, test end-to-end

DELIVERABLE: Multi-cycle sessions where the second debate references the
first. Org context visible in every model's response.

---

### WEEK 3 — Multi-Stakeholder Prompting & Structured Output
Owner: Person B (multi-prompt) + Person C (structured output)

GOALS:
- Multiple stakeholders can contribute prompts to the same debate cycle
  (tagged by who said what)
- Models output structured reactions (agree/disagree/refine with specific
  references) instead of free-form text

TASKS:
- [ ] Person B: Modify the prompt loop so multiple stakeholders can add
      input before triggering the cycle. Each message is tagged with the
      stakeholder's name and role. The combined prompt stream goes to Phase 1.
- [ ] Person C: Update buildReactionPrompt() to instruct models to output
      structured reactions: stance (agree/disagree/refine), target model,
      specific point being addressed, and rationale. Update extractField()
      or add JSON parsing to handle structured output.
- [ ] Team: Test with 2-3 stakeholders prompting the same cycle
- [ ] Team: Evaluate whether structured reactions improve synthesis quality

DELIVERABLE: A debate cycle initiated by multiple stakeholders, with models
producing structured cross-reactions that the synthesizer can reference
precisely.

---

### WEEK 4 — Simple GUI & Session Persistence
Owner: Person A (GUI) + Person B (file persistence)

GOALS:
- A basic Swing GUI showing the prompt input, three model panes, and the
  synthesis report — so the user can visualize the debate
- Sessions saved to disk so they can be reviewed later

TASKS:
- [ ] Person A: Build a Java Swing window with:
      - Top: stakeholder selector dropdown + prompt text field
      - Middle: three side-by-side panels (Claude / GPT / Gemini)
      - Bottom: synthesis report panel
      - Wire the existing methods as button callbacks
- [ ] Person B: Save each completed cycle to a JSON or text file
      (timestamp, stakeholder, prompt, all 6 responses, synthesis).
      Add a "load previous session" option.
- [ ] Person C + D: Continue refining prompt engineering — test different
      agent profiles, reaction instructions, and synthesis formats
- [ ] Team: Integration testing with GUI + persistence + multi-stakeholder

DELIVERABLE: A visual interface that shows the full debate flow. Saved
sessions that can be reopened and reviewed.

---

### WEEK 5 — Polish, Testing & Final Presentation
Owner: Full team

GOALS:
- Clean, commented code that any team member can explain
- Robust error handling for API failures
- Final presentation prepared

TASKS:
- [ ] Add graceful degradation: if one model fails, continue with two
- [ ] Add retry logic with backoff for rate limits (429 errors)
- [ ] Code review: remove dead code, ensure consistent commenting style
- [ ] Write README.md: setup instructions, architecture overview,
      how to add new models
- [ ] Prepare demo script: show a 3-stakeholder debate with the GUI,
      highlight how different stakeholders get different recommendations
- [ ] Practice the presentation — each person explains their component

DELIVERABLE: Final submission. Working platform, clean code, documentation,
and a demo that shows the full vision.


## Future Vision (post-submission)

These are stretch goals discussed in our design sessions. Not in scope for
the 5-week deadline, but they define where the platform is headed:

- Proprietary orchestration model to replace Claude as synthesizer
- Web-based interface (replacing Swing) for real multi-user access
- Async stakeholder input (submit perspectives at different times)
- Dynamic debate rounds (orchestrator decides if more rounds are needed)
- Model-agnostic plugin system (add new LLMs without code changes)
- Bias detection layer in the synthesis phase
- Cost dashboard tracking token usage per model per session

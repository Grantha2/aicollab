# Executive Suite Migration Plan

## Overview

Transform the AI Collaboration Platform from a university prototype into a
professional **Executive Suite** — a polished decision-support tool with a
modern, button-driven GUI, rebranded architecture, and granular context
control. This plan covers three pillars: **Rebrand**, **GUI Overhaul**, and
**Context Control**.

---

## Pillar 1: Rebrand — Naming & Documentation

### 1.1 Orchestrator → Maestro

Rename the central coordination class from `Orchestrator` to `Maestro` to
convey authority and professionalism.

**Files to change:**

| File | What Changes |
|---|---|
| `Orchestrator.java` | Rename file to `Maestro.java`, rename class to `Maestro` |
| `Main.java` | All references: `Orchestrator` → `Maestro` |
| `MainGui.java` | Field type and construction: `Orchestrator` → `Maestro` |
| `NotesForClaude/PHILOSOPHY.md` | All mentions of "Orchestrator" → "Maestro" |
| `NotesForClaude/ROADMAP.md` | All mentions of "Orchestrator" → "Maestro" |
| `NotesForClaude/HANDOFF.md` | All mentions of "Orchestrator" → "Maestro" |

**Steps:**
1. Create `Maestro.java` as a copy of `Orchestrator.java`
2. Replace class name, constructor name, and all comments
3. Update all import/reference sites across the codebase
4. Delete old `Orchestrator.java`
5. Update documentation files
6. Verify `mvn clean compile` passes

### 1.2 Onion Model → Context Layering Architecture

Replace all references to "onion model" with **Context Layering Architecture**
(or **CLA** for short). This is more descriptive and professional.

**Files to change:**

| File | What Changes |
|---|---|
| `PromptBuilder.java` | All comments referencing "onion" → "Context Layering Architecture" |
| `NotesForClaude/PHILOSOPHY.md` | Section heading and body: "The Onion Model" → "Context Layering Architecture" |
| `NotesForClaude/ROADMAP.md` | Any "onion" references |

**Terminology mapping:**
- "Onion layers" → "Context layers"
- "Innermost to outermost" → "Foundation to surface" (or keep layer numbering)
- "Peeling the onion" → "Composing context layers"

---

## Pillar 2: Executive Suite GUI Overhaul

### 2.1 New Data Model — `SuiteButton`

Create a `SuiteButton.java` record/class that represents a single action
button in the Executive Suite.

```java
public class SuiteButton {
    private String id;           // Unique identifier (UUID)
    private String label;        // Display text ("Run Debate", "Export Report")
    private String category;     // Category name ("Debate", "Export", "Profile", "Custom")
    private String iconPath;     // Path to icon image (PNG/SVG in resources)
    private String description;  // Tooltip / short description of what button does
    private String actionType;   // What the button triggers (enum or string key)
    private Map<String, String> actionParams;  // Parameters for the action
    private int sortOrder;       // Position within its category group
}
```

**Key design decisions:**
- **Color is derived from category**, not stored per-button. A
  `CategoryColorMap` maps category names to `Color` objects. When a button's
  category changes, its color changes automatically.
- **Icons** are stored as image files in `src/main/resources/icons/`. Each
  button references an icon by filename. A default icon is used when none is
  specified.
- **Grouping** is automatic: buttons with the same category appear together,
  bordered by the category's color.

### 2.2 Category → Color System

Create `CategoryColorMap.java` to manage the mapping between categories and
colors.

```java
public class CategoryColorMap {
    // Default palette — professional, accessible colors
    private static final Map<String, Color> DEFAULTS = Map.of(
        "Debate",    new Color(41, 98, 255),    // Blue
        "Export",    new Color(0, 150, 136),     // Teal
        "Profile",   new Color(156, 39, 176),   // Purple
        "Context",   new Color(255, 152, 0),    // Amber
        "Analysis",  new Color(76, 175, 80),    // Green
        "Custom",    new Color(120, 144, 156)   // Blue-grey
    );

    public Color colorForCategory(String category);
    public void setColor(String category, Color color);
    public List<String> getAllCategories();
    public void addCategory(String name, Color color);
}
```

**Rules:**
- New categories created by users get auto-assigned the next unused color from
  a rotating palette.
- Users can override any category's color via the Settings menu.
- Button border, header stripe, and icon tint all derive from the category
  color.

### 2.3 Button Spawner — "Create New Button" Form

Add a "+" button (always visible in the toolbar or as a floating action) that
opens `ButtonCreatorDialog.java`.

**Form fields:**

| Field | Type | Description |
|---|---|---|
| Label | Text input | Button display name |
| Category | Dropdown + "New..." option | Determines color grouping |
| Icon | File chooser + preview | Select PNG/SVG from disk or built-in library |
| Action Type | Dropdown | What the button does (see action types below) |
| Action Parameters | Dynamic sub-form | Changes based on action type |
| Description | Text area | Tooltip text |
| Placement | Radio buttons | "Toolbar", "Side Panel", "Quick Actions Bar" |

**Action types (extensible):**

| Action Type | What It Does | Parameters |
|---|---|---|
| `RUN_DEBATE` | Triggers a debate cycle | Pre-filled prompt (optional), stakeholder override |
| `SWITCH_PROFILE` | Switches active profile set | Profile set name |
| `EXPORT_SESSION` | Exports current session | Format (JSON, TXT, PDF) |
| `OPEN_CONTEXT_MENU` | Opens context control panel | Pre-selected layer |
| `CUSTOM_PROMPT` | Sends a specific prompt to one or all models | Prompt text, target model(s) |
| `SPAWN_BUTTON` | Meta-action: opens the button creator again | Pre-filled category |
| `MACRO` | Runs a sequence of other actions | Ordered list of action IDs |

**Persistence:**
- Buttons are saved to `buttons.json` in the project directory.
- Format: JSON array of `SuiteButton` objects.
- Loaded at startup, saved on every create/edit/delete.

### 2.4 Icon System

**Directory structure:**
```
src/main/resources/
  icons/
    builtin/
      debate.png
      export.png
      profile.png
      context.png
      settings.png
      add.png
      analysis.png
      report.png
    custom/          ← user-added icons copied here
```

**Implementation:**
- `IconLoader.java` — utility class that loads and caches icons, scales them
  to button size (32x32 default, 48x48 large mode).
- Built-in icons ship with the JAR. Custom icons are copied into a local
  `custom/` directory on import.
- If an icon file is missing, fall back to a colored circle with the first
  letter of the button label.

### 2.5 Redesigned MainGui Layout

Replace the current flat toolbar with a **grouped button panel** on the left
side and keep the debate visualization in the center.

```
+---------------------------------------------------------------+
| Menu Bar: [File] [Settings] [Context] [Help]                  |
+----------+----------------------------------------------------+
|          |  +----------+ +----------+ +----------+            |
| BUTTON   |  | Claude   | | GPT      | | Gemini   |            |
| PANEL    |  | Stream   | | Stream   | | Stream   |            |
|          |  |          | |          | |          |            |
| [Debate] |  |          | |          | |          |            |
|  Run     |  +----------+ +----------+ +----------+            |
|  Quick   |  +--------------------------------------------+    |
|          |  | Synthesis Report                           |    |
| [Export] |  |                                            |    |
|  JSON    |  +--------------------------------------------+    |
|  Report  |  +--------------------------------------------+    |
|          |  | Prompt Input                               |    |
| [Profile]|  |                                            |    |
|  Switch  |  +--------------------------------------------+    |
|  Create  |                                                    |
|          |                                                    |
| [  +  ]  |   ← "Create New Button" always at the bottom      |
+----------+----------------------------------------------------+
| Status Bar                                                    |
+---------------------------------------------------------------+
```

**Button rendering:**
- Each button is a `JPanel` containing:
  - Icon (left, 32x32)
  - Label (center, bold)
  - Category color stripe (left border, 4px wide)
- Buttons grouped under category headers (colored divider line + label)
- Hover effect: lighten background by 15%
- Click effect: darken background briefly
- Right-click: context menu with "Edit", "Delete", "Duplicate", "Move to..."

### 2.6 Button Grouping & Sorting

**Visual grouping rules:**
1. Buttons are grouped by `category`
2. Within each group, buttons are sorted by `sortOrder`
3. Category groups are sorted alphabetically (or by a user-defined order)
4. Each group has a colored header bar showing category name + color swatch
5. Groups are collapsible (click header to toggle)

**Drag-and-drop (stretch goal):**
- Buttons can be dragged between groups (changes their category)
- Buttons can be reordered within a group (changes sortOrder)

---

## Pillar 3: Context Control Menu

### 3.1 Purpose

Give users granular, real-time control over every piece of context sent to
the models. Currently, context layers are hardcoded in `PromptBuilder.java`.
The Context Control Menu makes each layer visible, editable, and toggleable.

### 3.2 Context Control Panel — `ContextControlDialog.java`

Accessible via:
- Menu Bar → Context → "Context Control..."
- A dedicated "Context" category button in the side panel
- Keyboard shortcut (Ctrl+Shift+C)

**Layout — tabbed panel with one tab per context layer:**

#### Tab 1: Team Context (Layer 1)
- **Text area** showing the current team context string
- **Edit** button to modify it inline
- **Toggle** (checkbox): "Include team context in prompts" — when unchecked,
  this layer is skipped entirely
- **Reset** button to restore `DEFAULT_TEAM_CONTEXT`
- **Preview** shows how this layer renders in a sample prompt

#### Tab 2: Agent Identities (Layer 2)
- **Three sub-panels** (one per model: Claude, GPT, Gemini)
- Each shows:
  - Agent name (editable)
  - Perspective (editable)
  - Lens description (editable)
  - `toBriefing()` preview (read-only, updates live)
- **Toggle per agent**: "Include this agent's identity" — allows sending a
  model without its persona (raw mode)
- **Swap button**: reassign which model gets which agent identity

#### Tab 3: Stakeholder Profile (Layer 3)
- Shows the **active stakeholder's** fields:
  - Name, Role, KPIs, Decision Authority, Background
- All fields editable (changes apply to current session only unless saved)
- **Toggle**: "Include stakeholder context"
- **Quick-switch** dropdown to change active stakeholder without leaving
  the dialog

#### Tab 4: Conversation History (Layer 4)
- Shows current history buffer content (read-only scrollable view)
- **Character count** and **estimated token count**
- **Max history slider**: adjust `maxHistoryChars` (currently from Config)
- **Clear history** button (with confirmation)
- **Toggle**: "Include conversation history in prompts"
- **Per-entry toggles**: checkboxes next to each past synthesis — uncheck to
  exclude specific entries from context

#### Tab 5: Prompt Preview (Read-Only)
- **Live composite view** showing the full assembled prompt as it would be
  sent to each model
- **Model selector** (Claude / GPT / Gemini) to see each model's version
- **Phase selector** (Phase 1 / Phase 2 / Phase 3) to preview different
  prompt types
- **Character count** and **estimated token count** for the assembled prompt
- **Copy to clipboard** button

### 3.3 Implementation — `ContextController.java`

A new class that sits between `MainGui` and `PromptBuilder`, holding the
toggle states and overrides.

```java
public class ContextController {
    private boolean includeTeamContext = true;
    private boolean includeAgentIdentity = true;
    private boolean includeStakeholderProfile = true;
    private boolean includeHistory = true;

    private String teamContextOverride = null;   // null = use default
    private Map<String, Boolean> agentToggles;   // per-model identity toggle
    private Set<Integer> excludedHistoryEntries;  // indices to skip

    // Called by PromptBuilder to check what to include
    public boolean shouldIncludeTeamContext();
    public boolean shouldIncludeAgent(String modelName);
    public boolean shouldIncludeStakeholder();
    public boolean shouldIncludeHistory();
    public String getEffectiveTeamContext();
    public List<Integer> getExcludedHistoryIndices();
}
```

**Integration with PromptBuilder:**
- `PromptBuilder` receives a `ContextController` reference
- Each `build*Prompt()` method checks the controller before including a layer
- When a layer is toggled off, its text is simply omitted (empty string)
- When overridden, the override text is used instead of the default

### 3.4 Context Presets

Allow users to save and load named context configurations.

```java
public class ContextPreset {
    private String name;            // "Full Context", "Minimal", "No History"
    private boolean teamContext;
    private boolean agentIdentity;
    private boolean stakeholder;
    private boolean history;
    private String teamContextOverride;
    // ... other overrides
}
```

Saved to `context-presets.json`. Loadable from Context menu or Context
Control dialog.

---

## Implementation Phases

### Phase 1: Rebrand (Estimated: small)
1. Rename `Orchestrator` → `Maestro` across all files
2. Rename "Onion Model" → "Context Layering Architecture" in all docs/comments
3. Compile, test, commit

### Phase 2: Data Model & Persistence (Estimated: medium)
1. Create `SuiteButton.java`
2. Create `CategoryColorMap.java`
3. Create `ButtonStore.java` (JSON persistence for buttons)
4. Create `IconLoader.java` (image loading + caching)
5. Add built-in icon resources
6. Unit test the data model

### Phase 3: Button Creator Dialog (Estimated: medium)
1. Build `ButtonCreatorDialog.java` with all form fields
2. Wire up action type → parameter sub-forms
3. Add icon preview and file chooser
4. Add category creation flow (new category → pick color)
5. Persist new buttons to `buttons.json`

### Phase 4: GUI Overhaul (Estimated: large)
1. Redesign `MainGui.java` with left button panel + center content
2. Implement `ButtonPanel.java` — renders grouped, colored buttons
3. Implement button rendering (icon + label + color stripe)
4. Wire button clicks to their action types
5. Add right-click context menu (edit, delete, duplicate)
6. Add category group headers with collapse/expand
7. Add hover/click visual effects
8. Place "+" (Create New Button) at bottom of panel

### Phase 5: Context Control (Estimated: large)
1. Create `ContextController.java`
2. Modify `PromptBuilder.java` to consult `ContextController`
3. Build `ContextControlDialog.java` (5-tab layout)
4. Wire toggles and overrides to live prompt assembly
5. Add prompt preview tab with live rendering
6. Add context presets (save/load)
7. Add menu bar entry and keyboard shortcut

### Phase 6: Polish & Integration
1. End-to-end testing: create button → use button → verify action
2. Test context toggles: disable layers → verify prompts change
3. Ensure CLI mode (`Main.java`) still works (no GUI dependency)
4. Update all documentation (PHILOSOPHY.md, ROADMAP.md, HANDOFF.md)
5. Screenshot the new GUI for README

---

## File Inventory (New & Modified)

### New Files
| File | Purpose |
|---|---|
| `Maestro.java` | Renamed from `Orchestrator.java` |
| `SuiteButton.java` | Button data model |
| `CategoryColorMap.java` | Category → color mapping |
| `ButtonStore.java` | JSON persistence for buttons |
| `ButtonCreatorDialog.java` | Form to create/edit buttons |
| `ButtonPanel.java` | Left-side panel rendering grouped buttons |
| `IconLoader.java` | Load, cache, and scale icon images |
| `ContextController.java` | Toggle/override state for context layers |
| `ContextControlDialog.java` | 5-tab context control UI |
| `ContextPreset.java` | Named context configuration |
| `src/main/resources/icons/builtin/*.png` | Built-in button icons |

### Modified Files
| File | Changes |
|---|---|
| `MainGui.java` | New layout with button panel, context menu, Maestro reference |
| `Main.java` | `Orchestrator` → `Maestro` |
| `PromptBuilder.java` | Integrate `ContextController`, rename onion references |
| `NotesForClaude/PHILOSOPHY.md` | Rebrand terminology |
| `NotesForClaude/ROADMAP.md` | Rebrand terminology |
| `NotesForClaude/HANDOFF.md` | Rebrand terminology |

### Deleted Files
| File | Reason |
|---|---|
| `Orchestrator.java` | Replaced by `Maestro.java` |

---

## Design Principles (Carried Forward)

1. **Simple. Teachable. Commented.** — Every new class follows one-concept-per-file.
   New dialogs get block comments explaining their purpose.
2. **No framework magic** — Button persistence is plain JSON via Gson. No
   Spring, no DI container. Constructor injection only.
3. **Interface-first** — If button actions need an abstraction, define a
   `SuiteAction` interface before implementing concrete actions.
4. **Category = Color = Grouping** — One concept drives all three. No
   separate color pickers, grouping configs, and category names to keep in
   sync.

package collab;

// ============================================================
// ButtonStore.java — JSON persistence for SuiteButton objects.
//
// WHAT THIS CLASS DOES (one sentence):
// Loads and saves the user's custom buttons to buttons.json
// using Gson, following the same pattern as ProfileLibrary.
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ButtonStore {

    private static final String BUTTONS_FILE = "buttons.json";
    private static final String ORG_REFRESH = "Refresh Organization Context first. ";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public ButtonStore() {
        this(Path.of(BUTTONS_FILE));
    }

    public ButtonStore(Path filePath) {
        this.filePath = filePath;
    }

    public List<SuiteButton> loadButtons() {
        if (!Files.exists(filePath)) {
            return getDefaultButtons();
        }
        try {
            String json = Files.readString(filePath);
            Type listType = new TypeToken<List<SuiteButton>>() {}.getType();
            List<SuiteButton> buttons = gson.fromJson(json, listType);
            return buttons != null ? buttons : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[ButtonStore] Failed to load buttons: " + e.getMessage());
            return getDefaultButtons();
        }
    }

    public void saveButtons(List<SuiteButton> buttons) {
        try {
            Files.writeString(filePath, gson.toJson(buttons));
        } catch (IOException e) {
            System.err.println("[ButtonStore] Failed to save buttons: " + e.getMessage());
        }
    }

    /** Helper: creates a task template button with org context refresh prefix. */
    private static SuiteButton task(String label, String category, int sort,
                                    boolean simple, String template,
                                    List<String> questions, String style) {
        SuiteButton b = new SuiteButton(label, category, "TASK_TEMPLATE");
        b.setDescription(label);
        b.setIconPath("builtin/report.png");
        b.setSortOrder(sort);
        b.setSimpleMode(simple);
        b.setPromptTemplate(ORG_REFRESH + template);
        b.setFollowUpQuestions(questions);
        b.setStyleInstructions(style);
        return b;
    }

    // Default buttons that ship with the application.
    public static List<SuiteButton> getDefaultButtons() {
        List<SuiteButton> defaults = new ArrayList<>();

        // --- System buttons ---

        SuiteButton runDebate = new SuiteButton("Run Debate", "System", "RUN_DEBATE");
        runDebate.setDescription("Start a full 3-phase debate cycle");
        runDebate.setIconPath("builtin/debate.png");
        runDebate.setSortOrder(0);
        defaults.add(runDebate);

        SuiteButton switchProfile = new SuiteButton("Switch Profile", "System", "SWITCH_PROFILE");
        switchProfile.setDescription("Change the active profile set");
        switchProfile.setIconPath("builtin/profile.png");
        switchProfile.setSortOrder(1);
        defaults.add(switchProfile);

        SuiteButton contextControl = new SuiteButton("Context Control", "System", "OPEN_CONTEXT_MENU");
        contextControl.setDescription("Open the context layering control panel");
        contextControl.setIconPath("builtin/context.png");
        contextControl.setSortOrder(2);
        defaults.add(contextControl);

        SuiteButton editConfig = new SuiteButton("Settings", "System", "EDIT_CONFIG");
        editConfig.setDescription("Edit API keys and configuration");
        editConfig.setIconPath("builtin/settings.png");
        editConfig.setSortOrder(3);
        defaults.add(editConfig);

        SuiteButton spawnBtn = new SuiteButton("Create Button", "System", "SPAWN_BUTTON");
        spawnBtn.setDescription("Create a new task button");
        spawnBtn.setIconPath("builtin/add.png");
        spawnBtn.setSortOrder(4);
        defaults.add(spawnBtn);

        // --- Leadership ---
        defaults.add(task("Set Semester Priorities", "Leadership", 0, false,
                "Draft a semester priorities plan for the President. Convert goals into a concrete operating plan with: BLUF, priorities, why each matters, success measures, owners, milestone dates, major risks, and the next two weeks of action. Keep it specific, executive, and aligned with the organization's mission.",
                Arrays.asList("Term/semester covered", "Top 3-5 goals", "Why each goal matters now", "Success measures", "Key dates/milestones", "Owner and supporting roles", "Constraints/risks", "Decisions needed from E-board"),
                "Executive, structured, action-oriented. BLUF format."));
        defaults.add(task("President's Brief", "Leadership", 1, false,
                "Draft a President's Brief for the upcoming E-board meeting with agenda items, status updates, decisions needed, and risks to flag.",
                Arrays.asList("Meeting date", "Key agenda items", "Decisions to be made", "Status updates needed", "Concerns to raise"),
                "Executive summary style. Concise, decision-focused."));
        defaults.add(task("Assign Committee Chairs", "Leadership", 2, false,
                "Using member profiles from the Organization Context, recommend committee chair assignments based on skills, availability, and development goals. Explain rationale for each recommendation.",
                Arrays.asList("Which committees need chairs", "Any constraints on assignments", "Development priorities for members"),
                "Analytical, people-focused. Reference specific member strengths."));
        defaults.add(task("Stakeholder Update", "Leadership", 3, true,
                "Draft a stakeholder update email summarizing recent progress, upcoming plans, and any asks.",
                Arrays.asList("Audience (advisor, dean, sponsor, etc.)", "Key updates to share", "Upcoming plans", "Any asks or requests"),
                "Professional, concise, forward-looking."));
        defaults.add(task("Strategic Initiative Brief", "Leadership", 4, false,
                "Create a strategic initiative brief with problem statement, proposed solution, resources needed, timeline, and success metrics.",
                Arrays.asList("Initiative name", "Problem being solved", "Proposed approach", "Resources needed", "Timeline", "Success metrics"),
                "Structured brief format. Clear problem-solution framing."));

        // --- Meetings ---
        defaults.add(task("Meeting Agenda", "Meetings", 0, true,
                "Create a structured meeting agenda with time allocations, discussion items, and desired outcomes for each item.",
                Arrays.asList("Meeting type (E-board, committee, general)", "Date/time", "Attendees", "Topics to cover", "Time allocated"),
                "Clean, scannable. Time-boxed items."));
        defaults.add(task("Meeting Minutes", "Meetings", 1, false,
                "Draft meeting minutes from notes with attendees, decisions made, action items with owners and deadlines.",
                Arrays.asList("Meeting name/date", "Attendees present", "Discussion points", "Decisions made", "Action items with owners"),
                "Formal minutes format. Clear action items with deadlines."));
        defaults.add(task("E-Board Meeting Prep", "Meetings", 2, false,
                "Prepare a comprehensive E-board meeting packet with agenda, officer reports, financial summary, decisions needed, and old/new business.",
                Arrays.asList("Meeting date", "Reports to include", "Decisions needed", "Old business items", "New business items"),
                "Comprehensive packet format. Executive-ready."));
        defaults.add(task("Committee Report", "Meetings", 3, true,
                "Draft a committee report summarizing activities, progress against goals, challenges, and next steps.",
                Arrays.asList("Committee name", "Reporting period", "Activities completed", "Challenges encountered", "Next steps"),
                "Brief, results-focused. Highlight blockers."));
        defaults.add(task("One-on-One Prep", "Meetings", 4, true,
                "Prepare talking points for a one-on-one meeting including topics, feedback, and questions to ask.",
                Arrays.asList("Who are you meeting with", "Purpose of meeting", "Topics to discuss", "Feedback to give", "Questions to ask"),
                "Conversational prep format. Balanced feedback."));

        // --- Membership ---
        defaults.add(task("Recruitment Plan", "Membership", 0, false,
                "Create a semester membership recruitment plan with target demographics, outreach channels, recruitment events, messaging, and timeline.",
                Arrays.asList("Target membership count", "Target demographics", "Current membership size", "Recruitment budget", "Key selling points"),
                "Strategic, data-informed. Specific channels and dates."));
        defaults.add(task("Member Onboarding", "Membership", 1, false,
                "Create a new member onboarding guide with welcome steps, key resources, first-month milestones, and mentor assignment.",
                Arrays.asList("Organization name", "Key resources to share", "First-month milestones", "Mentor assignment process"),
                "Welcoming, clear steps. Progressive engagement."));
        defaults.add(task("Retention Strategy", "Membership", 2, false,
                "Develop a member retention strategy analyzing engagement patterns, identifying at-risk members, and proposing interventions.",
                Arrays.asList("Current retention rate", "Known pain points", "Exit survey data if available", "Current engagement metrics"),
                "Analytical, solution-oriented. Data-backed recommendations."));
        defaults.add(task("Member Survey", "Membership", 3, true,
                "Create a member satisfaction and engagement survey with well-crafted questions and response scales.",
                Arrays.asList("Survey purpose", "Key areas to assess", "Number of questions desired", "Distribution method"),
                "Clear, unbiased questions. Mix of quantitative and qualitative."));
        defaults.add(task("Alumni Engagement", "Membership", 4, true,
                "Draft an alumni engagement plan with outreach strategies, events, and mentorship opportunities.",
                Arrays.asList("Alumni database size", "Current alumni engagement level", "Budget", "Goals for alumni relations"),
                "Relationship-focused. Long-term value proposition."));

        // --- Events ---
        defaults.add(task("Event Planning Checklist", "Events", 0, false,
                "Create a comprehensive event planning checklist with timeline, task owners, budget items, logistics, and contingencies.",
                Arrays.asList("Event name", "Date", "Expected attendance", "Venue", "Budget", "Event type"),
                "Detailed, timeline-driven. Nothing falls through cracks."));
        defaults.add(task("Event Promotion Plan", "Events", 1, true,
                "Create an event promotion plan with channels, content calendar, messaging, and engagement targets.",
                Arrays.asList("Event name/date", "Target audience", "Promotion budget", "Available channels"),
                "Multi-channel, time-sequenced. Clear CTAs."));
        defaults.add(task("Post-Event Report", "Events", 2, false,
                "Draft a post-event report with attendance data, feedback summary, financial reconciliation, and lessons learned.",
                Arrays.asList("Event name", "Attendance numbers", "Budget vs actual spend", "Feedback received", "What worked and what didn't"),
                "Data-driven, honest assessment. Actionable lessons."));
        defaults.add(task("Sponsorship Proposal", "Events", 3, false,
                "Draft a sponsorship proposal with sponsorship tiers, benefits for sponsors, audience demographics, and ROI projections.",
                Arrays.asList("Event/initiative name", "Sponsorship tiers and pricing", "Benefits for sponsors", "Target sponsors", "Budget needs"),
                "Professional, value-focused. Clear ROI for sponsors."));
        defaults.add(task("Event Budget", "Events", 4, true,
                "Create a detailed event budget with line items, cost estimates, revenue sources, and contingency allocation.",
                Arrays.asList("Event name", "Expected attendance", "Venue cost", "Major expense categories", "Revenue sources"),
                "Detailed line items. Conservative estimates with contingency."));

        // --- External Affairs ---
        defaults.add(task("Partnership Proposal", "External Affairs", 0, false,
                "Draft a partnership proposal outlining mutual benefits, scope of collaboration, timeline, deliverables, and success metrics.",
                Arrays.asList("Partner organization", "Partnership type", "Mutual benefits", "Scope of collaboration", "Timeline"),
                "Professional, mutual-value focused. Clear deliverables."));
        defaults.add(task("Outreach Email", "External Affairs", 1, true,
                "Draft a professional outreach email to a potential partner, sponsor, or collaborator with a clear value proposition and ask.",
                Arrays.asList("Recipient/organization", "Purpose of outreach", "Your value proposition", "Specific ask"),
                "Concise, professional. Strong opening, clear CTA."));
        defaults.add(task("Grant Application", "External Affairs", 2, false,
                "Draft a grant application narrative with project description, goals, methodology, budget justification, and evaluation plan.",
                Arrays.asList("Grant name/funder", "Project description", "Funding amount requested", "Goals/objectives", "Evaluation plan"),
                "Formal grant language. Evidence-based, outcome-focused."));
        defaults.add(task("Thank You to Partners", "External Affairs", 3, true,
                "Draft a thank-you communication to partners or sponsors acknowledging their contribution and its impact.",
                Arrays.asList("Partner name", "What they contributed", "Impact of their contribution", "Future collaboration plans"),
                "Warm, specific, genuine. Reference concrete impact."));
        defaults.add(task("Community Engagement Plan", "External Affairs", 4, false,
                "Create a community engagement plan identifying target communities, engagement strategies, events, and measurable outcomes.",
                Arrays.asList("Target communities", "Engagement goals", "Available resources", "Timeline"),
                "Strategic, inclusive. Measurable engagement metrics."));

        // --- Communications ---
        defaults.add(task("Newsletter Draft", "Communications", 0, true,
                "Draft an organizational newsletter with sections for updates, upcoming events, member spotlights, and calls to action.",
                Arrays.asList("Newsletter theme/title", "Key updates", "Upcoming events", "Member spotlights", "Call to action"),
                "Engaging, scannable. Mix of informative and celebratory."));
        defaults.add(task("Social Media Calendar", "Communications", 1, false,
                "Create a social media content calendar with posts, timing, platforms, hashtags, and engagement strategies.",
                Arrays.asList("Time period (week/month)", "Platforms to cover", "Key themes/campaigns", "Posting frequency", "Brand voice"),
                "Platform-specific. Varied content types. Engagement-focused."));
        defaults.add(task("Press Release", "Communications", 2, false,
                "Draft a professional press release following AP style with headline, dateline, lead paragraph, quotes, and boilerplate.",
                Arrays.asList("Announcement details", "Who/what/when/where/why", "Quotes and attribution", "Target media outlets"),
                "AP style. Inverted pyramid. Newsworthy tone."));
        defaults.add(task("Crisis Communication", "Communications", 3, false,
                "Draft crisis communication messaging with internal and external versions, talking points, and FAQ.",
                Arrays.asList("Nature of crisis", "Stakeholders affected", "Key facts known", "Desired outcome", "Spokesperson"),
                "Transparent, empathetic, factual. Separate internal/external messaging."));
        defaults.add(task("Email Campaign", "Communications", 4, true,
                "Draft an email campaign sequence with subject lines, body copy, and calls to action.",
                Arrays.asList("Campaign goal", "Target audience", "Number of emails in sequence", "Key message", "Call to action"),
                "Compelling subject lines. Progressive engagement. Clear CTAs."));

        // --- Marketing ---
        defaults.add(task("Flyer Brief", "Marketing", 0, true,
                "Create flyer content with headline, subheadline, body copy, call-to-action, and layout/visual suggestions.",
                Arrays.asList("What is the flyer for", "Target audience", "Key details (date/time/location)", "Brand colors/style"),
                "Eye-catching, concise. Clear visual hierarchy."));
        defaults.add(task("Brand Guidelines", "Marketing", 1, false,
                "Draft brand guidelines covering voice, tone, visual identity standards, messaging framework, and usage rules.",
                Arrays.asList("Organization name and mission", "Core values", "Target audience", "Existing brand elements"),
                "Comprehensive, consistent. Visual and verbal identity."));
        defaults.add(task("Campaign Strategy", "Marketing", 2, false,
                "Develop a marketing campaign strategy with goals, audience segmentation, channels, content plan, and KPIs.",
                Arrays.asList("Campaign name and goals", "Target audience segments", "Budget", "Timeline", "Key channels"),
                "Strategic, data-informed. Clear KPIs and measurement plan."));
        defaults.add(task("Pitch Deck Outline", "Marketing", 3, false,
                "Create a pitch deck outline with slide-by-slide content recommendations, key messages, and visual suggestions.",
                Arrays.asList("Audience (investors, partners, members)", "Purpose of pitch", "Key message/thesis", "Time limit", "Must-include content"),
                "Narrative arc. One idea per slide. Strong opening and close."));
        defaults.add(task("Promotional Copy", "Marketing", 4, true,
                "Write promotional copy optimized for a specific channel or purpose.",
                Arrays.asList("What are you promoting", "Platform/channel", "Target audience", "Tone", "Key selling points"),
                "Channel-appropriate. Compelling, concise. Strong CTA."));

        // --- Finance ---
        defaults.add(task("Budget Proposal", "Finance", 0, false,
                "Create a detailed budget proposal with revenue projections, expense categories, line items, and justifications.",
                Arrays.asList("Budget period", "Revenue sources", "Major expense categories", "Previous period actuals", "Funding requests"),
                "Detailed, justified. Conservative projections with contingency."));
        defaults.add(task("Expense Report", "Finance", 1, true,
                "Create an expense report summary with categories, amounts, receipts, and budget variance analysis.",
                Arrays.asList("Reporting period", "Expense categories", "Receipts/amounts", "Budget vs actual"),
                "Clean, categorized. Highlight variances."));
        defaults.add(task("Funding Request", "Finance", 2, false,
                "Draft a funding request with justification, expected ROI, timeline for use, and alternatives considered.",
                Arrays.asList("Amount requested", "Purpose", "Expected outcomes/ROI", "Timeline for use", "Alternative funding considered"),
                "Persuasive, well-justified. Clear ROI case."));
        defaults.add(task("Financial Report", "Finance", 3, false,
                "Draft a financial report with income/expense summary, balance overview, variance analysis, and forward projections.",
                Arrays.asList("Reporting period", "Income sources and amounts", "Expense categories", "Notable variances", "Upcoming commitments"),
                "Accurate, transparent. Variance explanations included."));
        defaults.add(task("Dues Structure Review", "Finance", 4, true,
                "Analyze current dues structure and recommend changes based on membership data, revenue needs, and competitive landscape.",
                Arrays.asList("Current dues amount", "Current membership size", "Revenue needs", "Competitor/peer dues", "Member feedback on pricing"),
                "Analytical, data-driven. Consider accessibility and value."));

        // --- Governance ---
        defaults.add(task("Constitution Amendment", "Governance", 0, false,
                "Draft a constitution or bylaws amendment with rationale, current text, proposed text, and impact analysis.",
                Arrays.asList("Section to amend", "Current text", "Proposed change", "Rationale", "Voting requirements"),
                "Formal, precise legal language. Clear redline comparison."));
        defaults.add(task("Policy Draft", "Governance", 1, false,
                "Draft a new organizational policy with scope, definitions, procedures, responsibilities, and enforcement mechanism.",
                Arrays.asList("Policy topic", "Scope", "Key provisions", "Enforcement mechanism", "Effective date"),
                "Clear, enforceable. Defined terms and procedures."));
        defaults.add(task("Election Plan", "Governance", 2, false,
                "Create an election plan with timeline, positions, nomination process, eligibility, campaign rules, and voting procedures.",
                Arrays.asList("Positions up for election", "Timeline", "Nomination process", "Eligibility requirements", "Voting method"),
                "Fair, transparent. Clear timeline and procedures."));
        defaults.add(task("Compliance Checklist", "Governance", 3, true,
                "Create a compliance checklist for university, regulatory, or organizational requirements.",
                Arrays.asList("Regulatory body or requirements source", "Items to check", "Deadline", "Current compliance status"),
                "Systematic, actionable. Status tracking for each item."));
        defaults.add(task("Transition Plan", "Governance", 4, false,
                "Create an officer transition plan with knowledge transfer schedule, key contacts, pending items, and institutional memory documentation.",
                Arrays.asList("Outgoing officer position", "Transition timeline", "Key knowledge to transfer", "Pending items", "Critical relationships to hand off"),
                "Comprehensive handoff. Nothing lost in transition."));

        // --- Operations ---
        defaults.add(task("Project Plan", "Operations", 0, false,
                "Create a project plan with phases, milestones, resource assignments, dependencies, risks, and timeline.",
                Arrays.asList("Project name", "Objectives", "Team members", "Timeline", "Key constraints"),
                "Structured, realistic. Clear milestones and dependencies."));
        defaults.add(task("Process Improvement", "Operations", 1, false,
                "Analyze a current process and recommend improvements with before/after comparison, expected benefits, and implementation steps.",
                Arrays.asList("Process name", "Current pain points", "Desired outcome", "Constraints", "Stakeholders affected"),
                "Lean/continuous improvement framing. Measurable benefits."));
        defaults.add(task("Resource Allocation", "Operations", 2, true,
                "Create a resource allocation plan mapping people, budget, and space to priorities for the upcoming period.",
                Arrays.asList("Resources available (people/budget/space)", "Priorities to resource", "Constraints", "Timeline"),
                "Balanced, priority-driven. Address constraints explicitly."));
        defaults.add(task("Risk Assessment", "Operations", 3, false,
                "Conduct a risk assessment identifying risks, assessing likelihood and impact, and proposing mitigation strategies.",
                Arrays.asList("Scope of assessment", "Known risks", "Risk tolerance level", "Critical assets or activities"),
                "Systematic risk matrix. Likelihood x Impact scoring."));
        defaults.add(task("Standard Operating Procedure", "Operations", 4, false,
                "Draft a standard operating procedure with numbered steps, responsibilities, quality checks, and exception handling.",
                Arrays.asList("Procedure name", "Purpose", "Steps involved", "Who performs each step", "Quality checks needed"),
                "Step-by-step, unambiguous. Testable by a new person."));

        // --- Culture ---
        defaults.add(task("Team Building Plan", "Culture", 0, true,
                "Create a team building plan with activities, schedule, goals, and logistics.",
                Arrays.asList("Team size", "Budget", "Goals (bonding/skills/fun)", "Time available", "Any constraints or preferences"),
                "Fun, inclusive. Mix of social and developmental."));
        defaults.add(task("Recognition Program", "Culture", 1, false,
                "Design a member or officer recognition program with categories, criteria, award types, frequency, and budget.",
                Arrays.asList("Recognition goals", "Categories/criteria", "Budget", "Frequency (monthly/semester/annual)", "Past programs if any"),
                "Motivating, fair. Multiple recognition paths."));
        defaults.add(task("Diversity & Inclusion Plan", "Culture", 2, false,
                "Create a diversity and inclusion plan with current assessment, goals, specific initiatives, metrics, and accountability.",
                Arrays.asList("Current diversity metrics or observations", "Goals", "Focus areas", "Budget", "Timeline"),
                "Evidence-based, actionable. Measurable accountability."));
        defaults.add(task("Mentorship Program", "Culture", 3, false,
                "Design a mentorship program with matching criteria, structure, meeting cadence, milestones, and support resources.",
                Arrays.asList("Program scope (new members, leadership dev, etc.)", "Mentor/mentee pool size", "Duration", "Goals", "Support resources"),
                "Structured but flexible. Clear expectations for both sides."));
        defaults.add(task("Organizational Values", "Culture", 4, false,
                "Facilitate defining or refreshing organizational values through a structured process with stakeholder input and actionable output.",
                Arrays.asList("Current values if any", "Stakeholder input gathered", "Core beliefs of the organization", "Aspirational traits"),
                "Inclusive process. Values that drive daily behavior."));

        // --- Crisis/Problem-Solving ---
        defaults.add(task("Crisis Response Plan", "Crisis/Problem-Solving", 0, false,
                "Create a crisis response plan with scenario identification, response protocols, communication chains, roles, and recovery steps.",
                Arrays.asList("Types of crises to plan for", "Key decision makers", "Communication channels", "External resources available"),
                "Clear chains of command. Quick-reference format."));
        defaults.add(task("Conflict Resolution", "Crisis/Problem-Solving", 1, true,
                "Draft a conflict resolution approach for a specific situation with analysis, options, and recommended steps.",
                Arrays.asList("Nature of conflict", "Parties involved", "Desired outcome", "Previous attempts to resolve", "Constraints"),
                "Balanced, empathetic. Focused on resolution not blame."));
        defaults.add(task("Root Cause Analysis", "Crisis/Problem-Solving", 2, false,
                "Conduct a root cause analysis using 5-Whys or fishbone diagram method to identify underlying causes and corrective actions.",
                Arrays.asList("Problem statement", "When first noticed", "Impact/severity", "Contributing factors suspected"),
                "Systematic, evidence-based. Multiple causal paths explored."));
        defaults.add(task("Decision Matrix", "Crisis/Problem-Solving", 3, true,
                "Create a weighted decision matrix for evaluating options against defined criteria with scoring and recommendation.",
                Arrays.asList("Decision to be made", "Options being considered", "Evaluation criteria", "Key stakeholders"),
                "Objective, transparent scoring. Clear recommendation with rationale."));
        defaults.add(task("Contingency Plan", "Crisis/Problem-Solving", 4, false,
                "Develop a contingency plan for a specific risk scenario with trigger conditions, response steps, resources, and recovery timeline.",
                Arrays.asList("Scenario being planned for", "Trigger conditions", "Available resources", "Acceptable outcomes", "Response timeline"),
                "Actionable when triggered. Pre-assigned roles and resources."));

        // --- Analysis (legacy, kept) ---
        defaults.add(task("Generate Agent Profile", "Analysis", 0, false,
                "Design an AI agent profile for the AI Collaboration Platform defining name, perspective/lens, communication style, expertise, and Context Layering Architecture approach.",
                Arrays.asList("Domain specialization", "Perspective or lens (analytical, creative, critical, etc.)", "Communication style", "Biases or priorities"),
                "Structured sections. Technical but accessible."));

        return defaults;
    }
}

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

        // --- Communication task templates ---

        SuiteButton thankYou = new SuiteButton("Thank You Note", "Communication", "TASK_TEMPLATE");
        thankYou.setDescription("Generate a professional thank you note");
        thankYou.setIconPath("builtin/report.png");
        thankYou.setSortOrder(0);
        thankYou.setSimpleMode(true);
        thankYou.setPromptTemplate(
                "Write a thoughtful, professional thank you note. " +
                "The note should be warm but appropriate for the context. " +
                "Keep it concise (2-3 paragraphs) unless otherwise specified.");
        thankYou.setFollowUpQuestions(Arrays.asList(
                "Who are you thanking?",
                "What are you thanking them for?",
                "What is your relationship to them? (colleague, client, mentor, etc.)",
                "Any specific details to mention?"));
        thankYou.setStyleInstructions(
                "Warm, sincere, professional. Avoid overly casual language. " +
                "Match formality to the relationship described.");
        defaults.add(thankYou);

        SuiteButton socialMedia = new SuiteButton("Social Media Caption", "Communication", "TASK_TEMPLATE");
        socialMedia.setDescription("Generate an engaging social media caption");
        socialMedia.setIconPath("builtin/report.png");
        socialMedia.setSortOrder(1);
        socialMedia.setSimpleMode(true);
        socialMedia.setPromptTemplate(
                "Create an engaging social media caption. " +
                "Include relevant hashtags. Keep the tone authentic and platform-appropriate.");
        socialMedia.setFollowUpQuestions(Arrays.asList(
                "What platform is this for? (Instagram, LinkedIn, Twitter/X, etc.)",
                "What is the post about?",
                "What is the goal? (engagement, awareness, promotion, etc.)",
                "Any brand voice or tone preferences?"));
        socialMedia.setStyleInstructions(
                "Engaging, concise, platform-appropriate. " +
                "Include a call-to-action when relevant. Add 3-5 hashtags.");
        defaults.add(socialMedia);

        SuiteButton pressRelease = new SuiteButton("Press Release", "Communication", "TASK_TEMPLATE");
        pressRelease.setDescription("Draft a professional press release");
        pressRelease.setIconPath("builtin/report.png");
        pressRelease.setSortOrder(2);
        pressRelease.setSimpleMode(false);
        pressRelease.setPromptTemplate(
                "Draft a professional press release following AP style guidelines. " +
                "Include a compelling headline, dateline, lead paragraph with the 5 W's, " +
                "supporting quotes, and a boilerplate section.");
        pressRelease.setFollowUpQuestions(Arrays.asList(
                "What is the announcement about?",
                "Who is the organization making the announcement?",
                "Who should be quoted and what is their title?",
                "What is the target audience?",
                "When is the event/launch date?"));
        pressRelease.setStyleInstructions(
                "Formal, objective, AP style. Inverted pyramid structure. " +
                "Third person. Factual and newsworthy tone.");
        defaults.add(pressRelease);

        // --- Creative task templates ---

        SuiteButton flyer = new SuiteButton("Generate Flyer", "Creative", "TASK_TEMPLATE");
        flyer.setDescription("Generate flyer content and layout suggestions");
        flyer.setIconPath("builtin/report.png");
        flyer.setSortOrder(0);
        flyer.setSimpleMode(true);
        flyer.setPromptTemplate(
                "Create the text content and layout suggestions for a flyer. " +
                "Include headline, subheadline, body copy, call-to-action, " +
                "and visual/layout recommendations.");
        flyer.setFollowUpQuestions(Arrays.asList(
                "What is the flyer promoting?",
                "Who is the target audience?",
                "What are the key details (date, time, location, price)?",
                "What is the desired size? (letter, half-page, A4, etc.)",
                "Any brand colors or visual style preferences?"));
        flyer.setStyleInstructions(
                "Eye-catching, concise copy. Use action words. " +
                "Prioritize scannability with clear hierarchy.");
        defaults.add(flyer);

        // --- Analysis task templates ---

        SuiteButton agentProfile = new SuiteButton("Generate Agent Profile", "Analysis", "TASK_TEMPLATE");
        agentProfile.setDescription("Generate an AI agent profile for the collaboration platform");
        agentProfile.setIconPath("builtin/profile.png");
        agentProfile.setSortOrder(0);
        agentProfile.setSimpleMode(false);
        agentProfile.setPromptTemplate(
                "Design an AI agent profile for the AI Collaboration Platform. " +
                "The profile should define the agent's name, perspective/lens, " +
                "communication style, areas of expertise, and how it approaches " +
                "the Context Layering Architecture (team context, identity, stakeholder, history).");
        agentProfile.setFollowUpQuestions(Arrays.asList(
                "What domain should this agent specialize in?",
                "What perspective or lens should it use? (analytical, creative, critical, etc.)",
                "What communication style? (formal, conversational, technical, etc.)",
                "Should it have any biases or priorities?"));
        agentProfile.setStyleInstructions(
                "Structured output with clear sections. " +
                "Technical but accessible. Include example prompts the agent would excel at.");
        defaults.add(agentProfile);

        return defaults;
    }
}

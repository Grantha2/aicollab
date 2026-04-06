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

        SuiteButton runDebate = new SuiteButton("Run Debate", "Debate", "RUN_DEBATE");
        runDebate.setDescription("Start a full 3-phase debate cycle");
        runDebate.setIconPath("builtin/debate.png");
        runDebate.setSortOrder(0);
        defaults.add(runDebate);

        SuiteButton switchProfile = new SuiteButton("Switch Profile", "Profile", "SWITCH_PROFILE");
        switchProfile.setDescription("Change the active profile set");
        switchProfile.setIconPath("builtin/profile.png");
        switchProfile.setSortOrder(0);
        defaults.add(switchProfile);

        SuiteButton createProfile = new SuiteButton("Create Profile", "Profile", "CREATE_PROFILE");
        createProfile.setDescription("Create a new profile set");
        createProfile.setIconPath("builtin/profile.png");
        createProfile.setSortOrder(1);
        defaults.add(createProfile);

        SuiteButton contextControl = new SuiteButton("Context Control", "Context", "OPEN_CONTEXT_MENU");
        contextControl.setDescription("Open the context layering control panel");
        contextControl.setIconPath("builtin/context.png");
        contextControl.setSortOrder(0);
        defaults.add(contextControl);

        SuiteButton editConfig = new SuiteButton("Settings", "Context", "EDIT_CONFIG");
        editConfig.setDescription("Edit API keys and configuration");
        editConfig.setIconPath("builtin/settings.png");
        editConfig.setSortOrder(1);
        defaults.add(editConfig);

        return defaults;
    }
}

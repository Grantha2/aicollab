package cowork.buttons;

// ButtonStore: default set when the file is absent or malformed, no debate-era
// defaults, unique ids, and a save/load round trip through Gson.

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ButtonStoreTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setHome() {
        System.setProperty("cowork.home", tempDir.toString());
    }

    @AfterEach
    void clearHome() {
        System.clearProperty("cowork.home");
    }

    @Test
    void defaultsLoadWhenFileAbsent() {
        ButtonStore store = new ButtonStore(tempDir.resolve("absent.json"));
        List<SuiteButton> buttons = store.loadButtons();
        assertEquals(ButtonStore.getDefaultButtons().size(), buttons.size());
        assertTrue(buttons.size() > 50, "expected the full default set");
    }

    @Test
    void defaultConstructorUsesDataDirUnderCoworkHome() {
        List<SuiteButton> buttons = new ButtonStore().loadButtons();
        assertFalse(buttons.isEmpty(), "no buttons.json under cowork.home, so defaults must load");
    }

    @Test
    void noDefaultIsDebateOrProfileRelated() {
        for (SuiteButton b : ButtonStore.getDefaultButtons()) {
            assertNotEquals("RUN_DEBATE", b.getActionType(), b.getLabel());
            assertNotEquals("SWITCH_PROFILE", b.getActionType(), b.getLabel());
        }
    }

    @Test
    void defaultsCoverThirteenCategoriesWithUniqueIds() {
        List<SuiteButton> defaults = ButtonStore.getDefaultButtons();
        Set<String> ids = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (SuiteButton b : defaults) {
            assertTrue(ids.add(b.getId()), "duplicate id for " + b.getLabel());
            categories.add(b.getCategory());
        }
        assertEquals(14, categories.size(), "System + 12 task categories + Analysis");
        assertTrue(categories.containsAll(List.of("System", "Leadership", "Meetings", "Membership", "Events",
                "External Affairs", "Communications", "Marketing", "Finance", "Governance", "Operations",
                "Culture", "Crisis/Problem-Solving")));
    }

    @Test
    void saveThenLoadRoundTrips() {
        Path file = tempDir.resolve("nested").resolve("buttons.json");
        ButtonStore store = new ButtonStore(file);
        List<SuiteButton> defaults = ButtonStore.getDefaultButtons();

        store.saveButtons(defaults);
        assertTrue(Files.exists(file));

        List<SuiteButton> loaded = store.loadButtons();
        assertEquals(defaults.size(), loaded.size());
        for (int i = 0; i < defaults.size(); i++) {
            SuiteButton a = defaults.get(i);
            SuiteButton b = loaded.get(i);
            assertEquals(a.getId(), b.getId());
            assertEquals(a.getLabel(), b.getLabel());
            assertEquals(a.getCategory(), b.getCategory());
            assertEquals(a.getActionType(), b.getActionType());
            assertEquals(a.getSortOrder(), b.getSortOrder());
            assertEquals(a.getPromptTemplate(), b.getPromptTemplate());
            assertEquals(a.getFollowUpQuestions(), b.getFollowUpQuestions());
            assertEquals(a.getStyleInstructions(), b.getStyleInstructions());
        }
    }

    @Test
    void malformedFileFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ this is not json");
        List<SuiteButton> buttons = new ButtonStore(file).loadButtons();
        assertEquals(ButtonStore.getDefaultButtons().size(), buttons.size());
    }
}

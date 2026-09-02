package cowork.buttons;

// SuiteButton: Gson round trip of a TASK_TEMPLATE button, no simpleMode field,
// and toTaskContext() maps label -> taskName.

import com.google.gson.Gson;
import cowork.context.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuiteButtonTest {

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

    private static SuiteButton sample() {
        SuiteButton b = new SuiteButton("Thank You Note", "Communications", "TASK_TEMPLATE");
        b.setDescription("Draft a thank-you note");
        b.setIconPath("builtin/report.png");
        b.setSortOrder(3);
        b.setPromptTemplate("Write a thank you note for: {user_input}");
        b.setFollowUpQuestions(List.of("Who are you thanking?", "What did they do?"));
        b.setStyleInstructions("Warm, specific, brief.");
        b.putParam("value", "unused");
        return b;
    }

    @Test
    void gsonRoundTripPreservesTaskTemplateFields() {
        Gson gson = new Gson();
        SuiteButton original = sample();
        String json = gson.toJson(original);
        SuiteButton copy = gson.fromJson(json, SuiteButton.class);

        assertEquals(original.getId(), copy.getId());
        assertEquals("Thank You Note", copy.getLabel());
        assertEquals("Communications", copy.getCategory());
        assertEquals("TASK_TEMPLATE", copy.getActionType());
        assertEquals(3, copy.getSortOrder());
        assertEquals("Write a thank you note for: {user_input}", copy.getPromptTemplate());
        assertEquals(List.of("Who are you thanking?", "What did they do?"), copy.getFollowUpQuestions());
        assertEquals("Warm, specific, brief.", copy.getStyleInstructions());
        assertEquals("unused", copy.getParam("value"));
        assertTrue(copy.isTaskTemplate());
    }

    @Test
    void noSimpleModeField() {
        assertFalse(new Gson().toJson(sample()).contains("simpleMode"));
        assertThrows(NoSuchFieldException.class, () -> SuiteButton.class.getDeclaredField("simpleMode"));
        assertThrows(NoSuchMethodException.class, () -> SuiteButton.class.getMethod("isSimpleMode"));
    }

    @Test
    void toTaskContextCopiesTemplateAndUsesLabelAsTaskName() {
        TaskContext ctx = sample().toTaskContext();
        assertNotNull(ctx);
        assertEquals("Thank You Note", ctx.getTaskName());
        assertEquals("Write a thank you note for: {user_input}", ctx.getPromptTemplate());
        assertEquals(2, ctx.getFollowUpQuestions().size());
        assertEquals("Warm, specific, brief.", ctx.getStyleInstructions());
    }

    @Test
    void toTaskContextIsNullWithoutTemplateData() {
        SuiteButton plain = new SuiteButton("Settings", "System", "EDIT_CONFIG");
        assertNull(plain.toTaskContext());
        assertFalse(plain.isTaskTemplate());
    }

    @Test
    void nullFollowUpQuestionsBecomeEmptyList() {
        SuiteButton b = new SuiteButton();
        b.setFollowUpQuestions(null);
        assertNotNull(b.getFollowUpQuestions());
        assertTrue(b.getFollowUpQuestions().isEmpty());
    }
}

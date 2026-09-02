package cowork.data;

import cowork.context.OrganizationContext;
import cowork.data.WorkflowDefinition.OutputFormat;
import cowork.data.WorkflowDefinition.TriggerType;
import cowork.data.WorkflowDefinition.WriteBackPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** resolvePrompt() placeholder substitution, plus a WorkflowStore round trip of the enum fields. */
class WorkflowDefinitionTest {

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
    }

    private static WorkflowDefinition workflow(String template, List<String> questions) {
        WorkflowDefinition wf = new WorkflowDefinition("w1", "Board memo", "Drafts a memo", "Comms");
        wf.setInputQuestions(questions);
        wf.setPromptTemplate(template);
        return wf;
    }

    @Test
    void resolvePromptSubstitutesFieldsAndInputs() {
        OrganizationContext ctx = new OrganizationContext();
        ctx.setTopPriorities("Grow membership");
        ctx.setPendingDecisions("Venue");
        WorkflowDefinition wf = workflow(
            "Priorities: {topPriorities}; decide {pendingDecisions}. Write for {input:0} in a {input:1} tone.",
            List.of("Audience?", "Tone?"));

        String resolved = wf.resolvePrompt(ctx, Map.of("0", "the board", "1", "formal"));

        assertEquals("Priorities: Grow membership; decide Venue. Write for the board in a formal tone.", resolved);
    }

    @Test
    void unansweredInputsBecomeEmptyAndUnknownPlaceholdersSurvive() {
        WorkflowDefinition wf = workflow("A{input:0}B {input:7} {notAField}", List.of("Only question"));
        assertEquals("AB {input:7} {notAField}", wf.resolvePrompt(new OrganizationContext(), Map.of()));
    }

    @Test
    void defaultConstructorAssignsShortIdAndDefaults() {
        WorkflowDefinition wf = new WorkflowDefinition();
        assertEquals(8, wf.getId().length());
        assertEquals(TriggerType.MANUAL, wf.getTriggerType());
        assertEquals(WriteBackPolicy.NONE, wf.getWriteBackPolicy());
        assertEquals(OutputFormat.REPORT, wf.getOutputFormat());
        assertTrue(wf.isEnabled());
        assertEquals("", wf.resolvePrompt(new OrganizationContext(), Map.of()));
    }

    @Test
    void storeRoundTripsDefinitionsIncludingEnums() {
        Path file = tempDir.resolve("workflows.json");
        WorkflowDefinition wf = workflow("Hello {input:0}", List.of("Name?"));
        wf.setTriggerType(TriggerType.SCHEDULED);
        wf.setWriteBackPolicy(WriteBackPolicy.APPROVAL_ALL);
        wf.setOutputFormat(OutputFormat.EMAIL_DRAFT);
        wf.setCadence("weekly");
        wf.setRequiredContextLayers(List.of("org"));

        WorkflowStore store = new WorkflowStore(file);
        store.add(wf);
        store.add(new WorkflowDefinition("w2", "Other", "", "Ops"));
        wf.setName("Board memo v2");
        store.update(wf);
        store.remove("w2");

        WorkflowStore reloaded = new WorkflowStore(file);
        assertEquals(1, reloaded.getAll().size());
        WorkflowDefinition back = reloaded.getById("w1");
        assertEquals("Board memo v2", back.getName());
        assertEquals(TriggerType.SCHEDULED, back.getTriggerType());
        assertEquals(WriteBackPolicy.APPROVAL_ALL, back.getWriteBackPolicy());
        assertEquals(OutputFormat.EMAIL_DRAFT, back.getOutputFormat());
        assertEquals("weekly", back.getCadence());
        assertEquals(List.of("Name?"), back.getInputQuestions());
        assertEquals(List.of("org"), back.getRequiredContextLayers());
        assertEquals("Hello Ann", back.resolvePrompt(new OrganizationContext(), Map.of("0", "Ann")));
        assertNull(reloaded.getById("w2"));
    }
}

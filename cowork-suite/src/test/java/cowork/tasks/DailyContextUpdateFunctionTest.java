package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.context.OrganizationContext;
import cowork.context.ProposedChange;
import cowork.context.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing tolerance of DailyContextUpdateFunction: plain arrays, fenced arrays, garbage. */
class DailyContextUpdateFunctionTest {

    @TempDir Path tempDir;

    private OrganizationContext org;
    private DailyContextUpdateFunction fn;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
        org = new OrganizationContext();
        fn = new DailyContextUpdateFunction(org, new ReconciliationService(org, new ContextChangeLog()));
    }

    @Test
    void parsesPlainJsonArray() {
        List<ProposedChange> out = fn.parseResponse("""
            [{"field": "topPriorities", "value": "1. Ship v1", "reason": "user said so"},
             {"field": "currentMetrics", "value": "42 members", "reason": "headcount"}]
            """);

        assertEquals(2, out.size());
        assertEquals("topPriorities", out.get(0).fieldName());
        assertEquals("1. Ship v1", out.get(0).proposedValue());
        assertEquals("daily_update", out.get(0).source());
        assertEquals("currentMetrics", out.get(1).fieldName());
    }

    @Test
    void stripsMarkdownFences() {
        List<ProposedChange> out = fn.parseResponse("""
            ```json
            [{"field": "pendingDecisions", "value": "Approve budget", "reason": "new"}]
            ```
            """);

        assertEquals(1, out.size());
        assertEquals("pendingDecisions", out.get(0).fieldName());
        assertEquals("Approve budget", out.get(0).proposedValue());
    }

    @Test
    void toleratesSurroundingProse() {
        List<ProposedChange> out = fn.parseResponse(
            "Here are the updates:\n[{\"field\": \"currentMetrics\", \"value\": \"7 events\"}]\nLet me know!");

        assertEquals(1, out.size());
        assertEquals("7 events", out.get(0).proposedValue());
    }

    @Test
    void garbageYieldsEmptyListWithoutThrowing() {
        assertTrue(fn.parseResponse("Sorry, I cannot help with that.").isEmpty());
        assertTrue(fn.parseResponse("[this is not json ]").isEmpty());
        assertTrue(fn.parseResponse("[1, 2, 3]").isEmpty());
        assertTrue(fn.parseResponse("[{\"field\": \"x\", \"value\": {\"nested\": true}}]").isEmpty());
        assertTrue(fn.parseResponse("").isEmpty());
        assertTrue(fn.parseResponse(null).isEmpty());
    }

    @Test
    void buildPromptIncludesFieldsAndNotes() {
        String prompt = fn.buildPrompt(List.of("topPriorities"), Map.of("topPriorities", "focus on recruiting"));

        assertNotNull(prompt);
        assertTrue(prompt.contains("Field: topPriorities"));
        assertTrue(prompt.contains("Label: Top Priorities"));
        assertTrue(prompt.contains("User Note: focus on recruiting"));
    }

    @Test
    void executeKeepsRawTextWhenNothingParses() {
        FakeLlmClient fake = new FakeLlmClient("I have no updates for you.");

        assertNull(fn.execute(fake, List.of("topPriorities"), Map.of()));
        assertEquals(1, fake.prompts().size());
        assertEquals("I have no updates for you.", fn.lastRawResponse());
    }

    @Test
    void executeReconcilesParsedProposals() {
        FakeLlmClient fake = new FakeLlmClient(
            "```json\n[{\"field\": \"currentMetrics\", \"value\": \"42 members\"}]\n```");

        ReconciliationService.ReconciliationResult result =
            fn.execute(fake, List.of("currentMetrics"), Map.of("currentMetrics", "we have 42 members"));

        assertNotNull(result);
        // The parsed proposal plus the automatic lastUpdated stamp.
        assertEquals(2, result.autoApplied().size() + result.needsApproval().size());
    }
}

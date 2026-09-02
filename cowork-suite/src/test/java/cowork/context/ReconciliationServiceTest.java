package cowork.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Classification rules, auto-apply side effects, and the approve/reject queue lifecycle. */
class ReconciliationServiceTest {

    @TempDir Path tempDir;

    private OrganizationContext ctx;
    private ContextChangeLog log;
    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
        ctx = new OrganizationContext();
        log = new ContextChangeLog(tempDir.resolve("context_changelog.jsonl"));
        service = new ReconciliationService(ctx, log);
    }

    private static ProposedChange aiChange(String field, String current, String proposed) {
        return new ProposedChange(field, current, proposed, "daily_update", 0.9);
    }

    @Test
    void strategicFieldRequiresApprovalAndIsQueuedUnapplied() {
        ProposedChange change = aiChange("topPriorities", "", "Ship v2");
        assertEquals(MergeDecision.APPROVAL_REQUIRED, service.classify(change));

        ReconciliationService.ReconciliationResult result = service.reconcile(List.of(change));

        assertEquals(List.of(change), result.needsApproval());
        assertTrue(result.autoApplied().isEmpty());
        assertEquals("", ctx.getTopPriorities());
        assertEquals(List.of(change), service.getApprovalQueue());
        assertTrue(log.readAll().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("org_context.json")), "nothing applied, nothing saved");
    }

    @Test
    void lowRiskHighConfidenceIsAutoAppliedAndLogged() {
        ProposedChange change = aiChange("currentMetrics", "", "42 active members");
        assertEquals(MergeDecision.SAFE_AUTO, service.classify(change));

        ReconciliationService.ReconciliationResult result = service.reconcile(List.of(change));

        assertEquals(List.of(change), result.autoApplied());
        assertTrue(result.needsApproval().isEmpty());
        assertTrue(service.getApprovalQueue().isEmpty());
        assertEquals("42 active members", ctx.getCurrentMetrics());
        ContextEntry<String> entry = ctx.getEntry("currentMetrics");
        assertEquals("daily_update", entry.getSource());
        assertEquals(0.9, entry.getConfidence());
        assertEquals(ContextStatus.APPROVED, entry.getStatus());

        List<ContextChangeLog.ChangeRecord> records = log.readAll();
        assertEquals(1, records.size());
        assertEquals("currentMetrics", records.get(0).field());
        assertEquals("auto_apply", records.get(0).action());
        assertEquals("42 active members", records.get(0).newValue());
        assertTrue(Files.exists(tempDir.resolve("org_context.json")), "auto-apply persists via AppPaths");
    }

    @Test
    void overwritingExistingContentFromAiRequiresApproval() {
        ctx.setCurrentMetrics("40 members");
        assertEquals(MergeDecision.APPROVAL_REQUIRED,
            service.classify(aiChange("currentMetrics", "40 members", "42 members")));
        assertEquals(MergeDecision.SAFE_AUTO,
            service.classify(aiChange("currentMetrics", "40 members", "40 members")));
    }

    @Test
    void metadataFieldsAndUserEditsAreAlwaysSafe() {
        ctx.setTopPriorities("existing");
        assertEquals(MergeDecision.SAFE_AUTO, service.classify(aiChange("lastUpdated", "x", "y")));
        assertEquals(MergeDecision.SAFE_AUTO, service.classify(aiChange("whatChangedSinceLastUpdate", "x", "y")));
        assertEquals(MergeDecision.SAFE_AUTO,
            service.classify(new ProposedChange("topPriorities", "existing", "new", "user_edit", 1.0)));
    }

    @Test
    void approveAppliesChangeAndClearsQueue() {
        ProposedChange change = aiChange("topPriorities", "", "Ship v2");
        service.reconcile(List.of(change));

        service.approve(change);

        assertEquals("Ship v2", ctx.getTopPriorities());
        assertEquals(ContextStatus.APPROVED, ctx.getEntry("topPriorities").getStatus());
        assertTrue(service.getApprovalQueue().isEmpty());
        List<ContextChangeLog.ChangeRecord> records = log.readAll();
        assertEquals(1, records.size());
        assertEquals("approve", records.get(0).action());
        assertTrue(Files.exists(tempDir.resolve("org_context.json")));
    }

    @Test
    void rejectClearsQueueWithoutApplying() {
        ProposedChange change = aiChange("pendingDecisions", "", "Pick a venue");
        service.reconcile(List.of(change));

        service.reject(change);

        assertEquals("", ctx.getPendingDecisions());
        assertTrue(service.getApprovalQueue().isEmpty());
        List<ContextChangeLog.ChangeRecord> records = log.readAll();
        assertEquals(1, records.size());
        assertEquals("reject", records.get(0).action());
    }

    @Test
    void approvalQueueIsASnapshot() {
        ProposedChange first = aiChange("topPriorities", "", "A");
        service.reconcile(List.of(first));
        List<ProposedChange> snapshot = service.getApprovalQueue();
        service.reconcile(List.of(aiChange("pendingDecisions", "", "B")));
        assertEquals(1, snapshot.size());
        assertEquals(2, service.getApprovalQueue().size());
        service.clearQueue();
        assertTrue(service.getApprovalQueue().isEmpty());
    }
}

package cowork.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Save/load round trip through Gson, AppPaths-based default location, and prompt-block rendering. */
class OrganizationContextTest {

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
    }

    @Test
    void saveThenLoadRoundTripsFieldsMetadataAndProfiles() {
        OrganizationContext ctx = new OrganizationContext();
        ctx.setTopPriorities("Grow membership");
        ctx.setCurrentTermDateRange("Fall 2026");
        ctx.setDefaultAudience("Board of directors");
        ctx.setDefaultOutputChannel("Email");
        ctx.updateField("currentBlockersRisks", "Budget shortfall", "daily_update", 0.7, ContextStatus.PROVISIONAL);
        OrganizationContext.MemberProfile ann = new OrganizationContext.MemberProfile("Ann", "Chair", "Runs meetings");
        ann.setSkills("Facilitation");
        ctx.addMemberProfile(ann);

        Path file = tempDir.resolve("sub").resolve("org_context.json");
        ctx.save(file);
        OrganizationContext loaded = OrganizationContext.load(file);

        assertEquals("Grow membership", loaded.getTopPriorities());
        assertEquals("Fall 2026", loaded.getCurrentTermDateRange());
        assertEquals("", loaded.getCurrentMetrics());
        assertEquals("Board of directors", loaded.getDefaultAudience());
        assertEquals("Email", loaded.getDefaultOutputChannel());

        ContextEntry<String> blockers = loaded.getEntry("currentBlockersRisks");
        assertEquals("Budget shortfall", blockers.getValue());
        assertEquals("daily_update", blockers.getSource());
        assertEquals(0.7, blockers.getConfidence());
        assertEquals(ContextStatus.PROVISIONAL, blockers.getStatus());
        assertEquals(ctx.getEntry("currentBlockersRisks").getLastUpdated(), blockers.getLastUpdated());

        assertEquals(1, loaded.getMemberProfiles().size());
        OrganizationContext.MemberProfile p = loaded.getMemberProfiles().get(0);
        assertEquals("Ann", p.getName());
        assertEquals("Chair", p.getRole());
        assertEquals("Runs meetings", p.getDetails());
        assertEquals("Facilitation", p.getSkills());

        assertEquals(ctx.buildContextBlock(), loaded.buildContextBlock());
    }

    @Test
    void defaultSaveAndLoadUseAppDataDirectory() {
        OrganizationContext ctx = new OrganizationContext();
        ctx.setPendingDecisions("Venue for gala");
        ctx.save();

        assertTrue(Files.exists(tempDir.resolve("org_context.json")));
        assertEquals("Venue for gala", OrganizationContext.load().getPendingDecisions());
    }

    @Test
    void loadOfMissingFileGivesEmptyContext() {
        OrganizationContext ctx = OrganizationContext.load(tempDir.resolve("nope.json"));
        for (String name : OrganizationContext.getFieldNames()) {
            assertEquals("", ctx.getEntry(name).getValue());
        }
        assertTrue(ctx.getMemberProfiles().isEmpty());
    }

    @Test
    void loadRestoresEntriesThatWereExplicitlyNull() throws Exception {
        Path file = tempDir.resolve("nulls.json");
        Files.writeString(file, "{\"topPriorities\": null, \"memberProfiles\": null}");
        OrganizationContext ctx = OrganizationContext.load(file);
        assertNotNull(ctx.getEntry("topPriorities"));
        assertEquals("", ctx.getTopPriorities());
        ctx.setTopPriorities("works after load");
        assertEquals("works after load", ctx.getTopPriorities());
        assertNotNull(ctx.getMemberProfiles());
    }

    @Test
    void buildContextBlockOmitsBlankFields() {
        OrganizationContext ctx = new OrganizationContext();
        ctx.setTopPriorities("Grow membership");
        ctx.setDefaultTone("Warm");

        String block = ctx.buildContextBlock();

        assertTrue(block.startsWith("=== ORGANIZATION CONTEXT ===\n"));
        assertTrue(block.contains("Top Priorities: Grow membership\n"));
        assertTrue(block.contains("Default Tone: Warm\n"));
        assertFalse(block.contains("Current Metrics"));
        assertFalse(block.contains("Pending Decisions"));
        assertFalse(block.contains("Default Audience"));
        assertFalse(block.contains("Member/Officer Profiles"));
    }

    @Test
    void buildContextBlockRendersProfilesWithOptionalParts() {
        OrganizationContext ctx = new OrganizationContext();
        ctx.addMemberProfile(new OrganizationContext.MemberProfile("Ann", "Chair", "Runs meetings"));
        ctx.addMemberProfile(new OrganizationContext.MemberProfile("Bo", "", null));

        String block = ctx.buildContextBlock();

        assertTrue(block.contains("--- Member/Officer Profiles ---\n- Ann (Chair): Runs meetings\n- Bo\n"));
        ctx.removeMemberProfile(0);
        assertFalse(ctx.buildContextBlock().contains("Ann"));
    }

    @Test
    void freshnessReportCoversEveryFieldWithItsOwnTtl() {
        OrganizationContext ctx = new OrganizationContext();
        Map<String, Freshness> report = ctx.getFreshnessReport();
        assertEquals(OrganizationContext.getFieldNames(), report.keySet().stream().toList());
        assertTrue(report.values().stream().allMatch(f -> f == Freshness.FRESH));

        // 20 days: past the 14-day strategic TTL but well inside the 30-day stable one.
        String twentyDaysAgo = Instant.now().minus(Duration.ofDays(20)).toString();
        ctx.getEntry("topPriorities").setLastUpdated(twentyDaysAgo);
        ctx.getEntry("preferredToneStyle").setLastUpdated(twentyDaysAgo);
        assertEquals(Freshness.NEEDS_CONFIRMATION, ctx.getFieldFreshness("topPriorities"));
        assertEquals(Freshness.AGING, ctx.getFieldFreshness("preferredToneStyle"));
        assertEquals(Freshness.NEEDS_CONFIRMATION, ctx.getFieldFreshness("noSuchField"));
        assertEquals("Top Priorities", OrganizationContext.getFieldLabel("topPriorities"));
        assertEquals(Duration.ofDays(14), OrganizationContext.getFieldTtl("topPriorities"));
    }
}

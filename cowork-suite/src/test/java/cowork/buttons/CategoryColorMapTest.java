package cowork.buttons;

// CategoryColorMap: palette rotation for unknown categories is stable, and the
// debate-era default keys (Debate/Export/Profile) are gone.

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryColorMapTest {

    // Mirrors the rotation palette inside CategoryColorMap.
    private static final List<Color> PALETTE = List.of(
            new Color(41, 98, 255), new Color(0, 150, 136), new Color(156, 39, 176), new Color(255, 152, 0),
            new Color(76, 175, 80), new Color(120, 144, 156), new Color(244, 67, 54), new Color(33, 150, 243));

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
    void unknownCategoryGetsPaletteColourAndIsStable() {
        CategoryColorMap map = new CategoryColorMap();
        assertFalse(map.hasCategory("Robotics"));

        Color first = map.colorForCategory("Robotics");
        assertNotNull(first);
        assertTrue(PALETTE.contains(first), "colour should come from the rotation palette");
        assertTrue(map.hasCategory("Robotics"));
        assertSame(first, map.colorForCategory("Robotics"));
        assertTrue(map.getAllCategories().contains("Robotics"));
    }

    @Test
    void debateEraCategoriesAreNotDefaults() {
        CategoryColorMap map = new CategoryColorMap();
        assertFalse(map.hasCategory("Debate"));
        assertFalse(map.hasCategory("Export"));
        assertFalse(map.hasCategory("Profile"));
    }

    @Test
    void executiveCategoriesAreDefaults() {
        CategoryColorMap map = new CategoryColorMap();
        for (String cat : List.of("System", "Leadership", "Meetings", "Membership", "Events", "External Affairs",
                "Communications", "Marketing", "Finance", "Governance", "Operations", "Culture",
                "Crisis/Problem-Solving")) {
            assertTrue(map.hasCategory(cat), cat);
        }
    }

    @Test
    void setColorOverridesDefault() {
        CategoryColorMap map = new CategoryColorMap();
        map.setColor("Finance", Color.MAGENTA);
        assertEquals(Color.MAGENTA, map.colorForCategory("Finance"));
    }
}

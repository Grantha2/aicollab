package collab;

// ============================================================
// CategoryColorMap.java — Maps button categories to colors.
//
// WHAT THIS CLASS DOES (one sentence):
// Maintains a mapping from category names (like "Debate", "Export")
// to colors, so every button in a category shares the same color.
//
// KEY DESIGN DECISION:
// Category = Color = Grouping. One concept drives all three.
// New categories auto-assign from a rotating palette.
// ============================================================

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoryColorMap {

    private final Map<String, Color> colorMap = new LinkedHashMap<>();

    // A rotating palette for auto-assigning colors to new categories.
    private static final Color[] PALETTE = {
        new Color(41, 98, 255),    // Blue
        new Color(0, 150, 136),    // Teal
        new Color(156, 39, 176),   // Purple
        new Color(255, 152, 0),    // Amber
        new Color(76, 175, 80),    // Green
        new Color(120, 144, 156),  // Blue-grey
        new Color(244, 67, 54),    // Red
        new Color(33, 150, 243),   // Light Blue
    };

    public CategoryColorMap() {
        // Default categories
        colorMap.put("Debate",   PALETTE[0]);
        colorMap.put("Export",   PALETTE[1]);
        colorMap.put("Profile",  PALETTE[2]);
        colorMap.put("Context",  PALETTE[3]);
        colorMap.put("Analysis", PALETTE[4]);
        colorMap.put("Custom",   PALETTE[5]);
    }

    public Color colorForCategory(String category) {
        return colorMap.computeIfAbsent(category, k -> {
            int idx = colorMap.size() % PALETTE.length;
            return PALETTE[idx];
        });
    }

    public void setColor(String category, Color color) {
        colorMap.put(category, color);
    }

    public List<String> getAllCategories() {
        return new ArrayList<>(colorMap.keySet());
    }

    public void addCategory(String name, Color color) {
        colorMap.put(name, color);
    }

    public boolean hasCategory(String name) {
        return colorMap.containsKey(name);
    }
}

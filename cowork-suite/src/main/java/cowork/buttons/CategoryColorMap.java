package cowork.buttons;

// Maps button categories to colours. Category = colour = grouping: one
// concept drives all three on the board. Unknown categories are assigned
// from a rotating palette and the assignment is remembered.

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoryColorMap {

    private final Map<String, Color> colorMap = new LinkedHashMap<>();

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
        colorMap.put("System",        new Color(66, 66, 66));
        colorMap.put("Context",       PALETTE[3]);
        colorMap.put("Analysis",      PALETTE[4]);
        colorMap.put("Custom",        PALETTE[5]);
        colorMap.put("Communication", PALETTE[7]);
        colorMap.put("Creative",      new Color(255, 87, 34));

        colorMap.put("Leadership",             new Color(25, 50, 120));
        colorMap.put("Meetings",               new Color(0, 150, 136));
        colorMap.put("Membership",             new Color(156, 39, 176));
        colorMap.put("Events",                 new Color(255, 152, 0));
        colorMap.put("External Affairs",       new Color(63, 81, 181));
        colorMap.put("Communications",         new Color(33, 150, 243));
        colorMap.put("Marketing",              new Color(233, 30, 99));
        colorMap.put("Finance",                new Color(76, 175, 80));
        colorMap.put("Governance",             new Color(121, 85, 72));
        colorMap.put("Operations",             new Color(96, 125, 139));
        colorMap.put("Culture",                new Color(255, 193, 7));
        colorMap.put("Crisis/Problem-Solving", new Color(244, 67, 54));
    }

    public Color colorForCategory(String category) {
        return colorMap.computeIfAbsent(category, k -> PALETTE[colorMap.size() % PALETTE.length]);
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

package collab;

// ============================================================
// IconLoader.java — Loads and caches button icons.
//
// WHAT THIS CLASS DOES (one sentence):
// Loads PNG icons from the resources directory, scales them to
// button size, and caches them so they're only loaded once.
//
// FALLBACK:
// If an icon file is missing, generates a colored circle with
// the first letter of the button label.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class IconLoader {

    private static final int DEFAULT_SIZE = 32;
    private final Map<String, ImageIcon> cache = new HashMap<>();

    public ImageIcon loadIcon(String iconPath, int size) {
        String cacheKey = iconPath + ":" + size;
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        ImageIcon icon = tryLoadFromResources(iconPath, size);
        if (icon != null) {
            cache.put(cacheKey, icon);
            return icon;
        }

        // Icon not found — will be handled by caller with fallback
        return null;
    }

    public ImageIcon loadIcon(String iconPath) {
        return loadIcon(iconPath, DEFAULT_SIZE);
    }

    // Creates a fallback icon: a colored circle with a letter.
    public static ImageIcon createFallbackIcon(String label, Color color, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw colored circle
        g.setColor(color);
        g.fillOval(2, 2, size - 4, size - 4);

        // Draw letter
        String letter = (label != null && !label.isEmpty())
                ? label.substring(0, 1).toUpperCase()
                : "?";
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size / 2));
        FontMetrics fm = g.getFontMetrics();
        int textX = (size - fm.stringWidth(letter)) / 2;
        int textY = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(letter, textX, textY);

        g.dispose();
        return new ImageIcon(img);
    }

    private ImageIcon tryLoadFromResources(String iconPath, int size) {
        if (iconPath == null || iconPath.isEmpty()) {
            return null;
        }

        // Try loading from classpath (resources directory)
        String resourcePath = "/icons/" + iconPath;
        java.net.URL url = getClass().getResource(resourcePath);
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }

        // Try loading from file system
        java.io.File file = new java.io.File(iconPath);
        if (file.exists()) {
            ImageIcon raw = new ImageIcon(file.getAbsolutePath());
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }

        return null;
    }
}

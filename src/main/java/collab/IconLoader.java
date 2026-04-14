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
import java.awt.geom.Ellipse2D;
import java.awt.image.BaseMultiResolutionImage;
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

    // ============================================================
    // createFallbackIcon() — Vector-style fallback icon.
    //
    // Returns an Icon that paints directly into the destination
    // Graphics2D instead of pre-rasterizing to a BufferedImage.
    // That matters on fractional HiDPI scales (125% / 150%) where
    // any pre-rasterized bitmap has to be resampled by Swing,
    // producing the soft "blurry" edges we were fighting. By
    // painting on the target Graphics2D we let Swing rasterize
    // the circle and letter at native physical resolution.
    // ============================================================
    public static Icon createFallbackIcon(String label, Color color, int size) {
        String letter = (label != null && !label.isEmpty())
                ? label.substring(0, 1).toUpperCase()
                : "?";
        return new LetterBadgeIcon(letter, color, size);
    }

    // Icon that draws a coloured disc with a centred capital letter.
    // Because we paint via the caller's Graphics2D, the JVM's HiDPI
    // transform is already applied, so every edge is crisp at 100%,
    // 125%, 150%, 200% — no bitmap resampling happens at all.
    private static final class LetterBadgeIcon implements Icon {
        private final String letter;
        private final Color color;
        private final int size;

        LetterBadgeIcon(String letter, Color color, int size) {
            this.letter = letter;
            this.color = color;
            this.size = size;
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                // Coloured disc — use Ellipse2D.Float so the shape is
                // described in floating-point coords; the rasterizer
                // then snaps to the physical pixel grid cleanly.
                float inset = 1f;
                g2.setColor(color);
                g2.fill(new Ellipse2D.Float(
                        x + inset, y + inset,
                        size - inset * 2, size - inset * 2));

                // Centred capital letter.
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * 0.55f)));
                FontMetrics fm = g2.getFontMetrics();
                int textX = x + (size - fm.stringWidth(letter)) / 2;
                int textY = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(letter, textX, textY);
            } finally {
                g2.dispose();
            }
        }
    }

    private ImageIcon tryLoadFromResources(String iconPath, int size) {
        if (iconPath == null || iconPath.isEmpty()) {
            return null;
        }

        // Try loading from classpath (resources directory)
        String resourcePath = "/icons/" + iconPath;
        java.net.URL url = getClass().getResource(resourcePath);
        if (url != null) {
            return multiResIcon(new ImageIcon(url).getImage(), size);
        }

        // Try loading from file system
        java.io.File file = new java.io.File(iconPath);
        if (file.exists()) {
            return multiResIcon(new ImageIcon(file.getAbsolutePath()).getImage(), size);
        }

        return null;
    }

    // Wrap a source image in a 1x/2x/3x BaseMultiResolutionImage so Java
    // picks the variant that matches the current display scaling instead
    // of bitmap-upscaling a fixed-size raster (which looks pixelated at
    // 150% / 200% / 300%).
    private static ImageIcon multiResIcon(Image src, int size) {
        BufferedImage x1 = scaleHighQuality(src, size, size);
        BufferedImage x2 = scaleHighQuality(src, size * 2, size * 2);
        BufferedImage x3 = scaleHighQuality(src, size * 3, size * 3);
        return new ImageIcon(new BaseMultiResolutionImage(x1, x2, x3));
    }

    // ============================================================
    // scaleHighQuality() — Crisp icon scaling for HiDPI displays.
    //
    // Image.getScaledInstance(..., SCALE_SMOOTH) is deprecated and
    // produces blurry results, especially when shrinking. Rendering
    // the source image into a BufferedImage with bicubic/bilinear
    // interpolation plus quality hints keeps edges sharp on both
    // standard and HiDPI screens.
    // ============================================================
    private static BufferedImage scaleHighQuality(Image src, int width, int height) {
        // Ensure the source image is fully loaded before we draw it.
        Image source = src;
        if (source.getWidth(null) <= 0 || source.getHeight(null) <= 0) {
            ImageIcon wait = new ImageIcon(source);
            source = wait.getImage();
        }

        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return dst;
    }
}

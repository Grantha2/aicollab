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
    // createFallbackIcon() — DPI-aware fallback icon.
    //
    // Returns an ImageIcon backed by a BaseMultiResolutionImage
    // containing 1x, 2x and 3x variants. On a 150% / 200% / 300%
    // scaled Windows display Java automatically picks the sharpest
    // variant instead of bitmap-upscaling the 1x bitmap (which is
    // what causes the jagged edges people see).
    // ============================================================
    public static ImageIcon createFallbackIcon(String label, Color color, int size) {
        String letter = (label != null && !label.isEmpty())
                ? label.substring(0, 1).toUpperCase()
                : "?";

        BufferedImage base = renderFallbackBitmap(letter, color, size, 1.0);
        BufferedImage x2   = renderFallbackBitmap(letter, color, size, 2.0);
        BufferedImage x3   = renderFallbackBitmap(letter, color, size, 3.0);

        Image multi = new BaseMultiResolutionImage(base, x2, x3);
        return new ImageIcon(multi);
    }

    // Renders one resolution variant of the fallback icon. The caller
    // always asks for the same logical `size`; `scale` controls how
    // many physical pixels we draw into so Java can pick the right
    // variant for the current display scaling.
    private static BufferedImage renderFallbackBitmap(String letter, Color color,
                                                      int size, double scale) {
        int px = (int) Math.round(size * scale);
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            // Draw colored circle, scaled.
            int inset = (int) Math.round(2 * scale);
            g.setColor(color);
            g.fillOval(inset, inset, px - inset * 2, px - inset * 2);

            // Draw letter, scaled.
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round((size / 2.0) * scale)));
            FontMetrics fm = g.getFontMetrics();
            int textX = (px - fm.stringWidth(letter)) / 2;
            int textY = (px - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(letter, textX, textY);
        } finally {
            g.dispose();
        }
        return img;
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

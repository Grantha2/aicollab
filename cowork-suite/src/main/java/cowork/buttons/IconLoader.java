package cowork.buttons;

// Loads, scales and caches button icons from the classpath (/icons/...) or
// the file system. When no bitmap exists, callers use createFallbackIcon(),
// a vector-painted letter badge that stays crisp at any HiDPI scale.

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
        }
        return icon;
    }

    public ImageIcon loadIcon(String iconPath) {
        return loadIcon(iconPath, DEFAULT_SIZE);
    }

    /** Coloured disc with the label's first letter, painted as a vector so it never resamples. */
    public static Icon createFallbackIcon(String label, Color color, int size) {
        String letter = (label != null && !label.isEmpty())
                ? label.substring(0, 1).toUpperCase()
                : "?";
        return new LetterBadgeIcon(letter, color, size);
    }

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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                float inset = 1f;
                g2.setColor(color);
                g2.fill(new Ellipse2D.Float(x + inset, y + inset, size - inset * 2, size - inset * 2));

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
        java.net.URL url = getClass().getResource("/icons/" + iconPath);
        if (url != null) {
            return multiResIcon(new ImageIcon(url).getImage(), size);
        }
        java.io.File file = new java.io.File(iconPath);
        if (file.exists()) {
            return multiResIcon(new ImageIcon(file.getAbsolutePath()).getImage(), size);
        }
        return null;
    }

    // 1x/2x/3x variants let Java pick the raster matching the display scale
    // instead of bitmap-upscaling one fixed size (pixelated at 150%+).
    private static ImageIcon multiResIcon(Image src, int size) {
        BufferedImage x1 = scaleHighQuality(src, size, size);
        BufferedImage x2 = scaleHighQuality(src, size * 2, size * 2);
        BufferedImage x3 = scaleHighQuality(src, size * 3, size * 3);
        return new ImageIcon(new BaseMultiResolutionImage(x1, x2, x3));
    }

    // Image.getScaledInstance(SCALE_SMOOTH) blurs when shrinking; bicubic
    // rendering into a BufferedImage keeps edges sharp.
    private static BufferedImage scaleHighQuality(Image src, int width, int height) {
        Image source = src;
        if (source.getWidth(null) <= 0 || source.getHeight(null) <= 0) {
            source = new ImageIcon(source).getImage();
        }
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return dst;
    }
}

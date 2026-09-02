package cowork;

// Entry point: enables HiDPI-aware rendering before any AWT class loads,
// then hands off to the Swing shell. No CLI mode.

import cowork.ui.MainGui;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        // Must run before Toolkit initialisation or the JVM ignores them.
        setIfAbsent("sun.java2d.uiScale.enabled", "true");
        setIfAbsent("sun.java2d.dpiaware", "true");
        setIfAbsent("awt.useSystemAAFontSettings", "true");
        setIfAbsent("swing.aatext", "true");
        MainGui.launch();
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}

package cowork.tasks;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

/**
 * Parent-window lookup for the small modal input dialogs tasks open. Tasks no longer hold a
 * panel reference, so the owner is the active application frame (null centres the dialog on
 * screen). Also answers whether a display exists at all, so optional prompts can be skipped
 * in tests and unattended runs.
 */
public final class TaskDialogs {

    private TaskDialogs() {}

    public static Frame owner() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active instanceof Frame frame) return frame;
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing()) return frame;
        }
        return null;
    }

    public static boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }
}

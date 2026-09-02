package cowork.config;

// AppPathsTest — the cowork.home system property must win over the working
// directory, and data()/asset() must resolve beneath it.

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clearOverride() {
        System.clearProperty("cowork.home");
    }

    @Test
    void systemPropertyOverrideWinsOverCwd() {
        System.clearProperty("cowork.home");
        Path before = AppPaths.base();
        if (System.getenv("COWORK_HOME") == null) {
            assertEquals(Path.of("").toAbsolutePath(), before);
        }

        System.setProperty("cowork.home", tmp.toString());
        Path expected = tmp.toAbsolutePath().normalize();
        assertEquals(expected, AppPaths.base());
        assertNotEquals(before, AppPaths.base());
        assertEquals(expected.resolve("buttons.json"), AppPaths.data("buttons.json"));
        assertEquals(expected.resolve("assets").resolve("fixtures/room_availability.html"),
                AppPaths.asset("fixtures/room_availability.html"));
    }

    @Test
    void blankPropertyIsIgnored() {
        // Do not build a Path from "   " to compare against: a trailing space is
        // an illegal path character on Windows and Path.of would throw here.
        System.setProperty("cowork.home", "   ");
        Path base = AppPaths.base();
        assertTrue(base.isAbsolute());
        assertFalse(base.toString().contains("   "));
        if (System.getenv("COWORK_HOME") == null) {
            assertEquals(Path.of("").toAbsolutePath(), base);
        }
    }
}

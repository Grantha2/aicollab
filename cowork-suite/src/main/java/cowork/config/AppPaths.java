package cowork.config;

// AppPaths — the single resolver for every data file and asset in the suite.
// Base dir precedence: -Dcowork.home, then $COWORK_HOME, then the working
// directory, so tests and packaged installs relocate all state without code
// changes and no store ever hard-codes Path.of("x.json").

import java.nio.file.Path;

public final class AppPaths {

    private AppPaths() {}

    public static Path base() {
        String prop = System.getProperty("cowork.home");
        if (prop != null && !prop.isBlank()) return Path.of(prop).toAbsolutePath().normalize();
        String env = System.getenv("COWORK_HOME");
        if (env != null && !env.isBlank()) return Path.of(env).toAbsolutePath().normalize();
        return Path.of("").toAbsolutePath();
    }

    public static Path data(String fileName) {
        return base().resolve(fileName);
    }

    public static Path asset(String relative) {
        return base().resolve("assets").resolve(relative);
    }
}

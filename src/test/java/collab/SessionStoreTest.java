package collab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionStoreTest {

    @Test
    void loadTurnsPreservesLiteralBackslashEscapes() throws Exception {
        Path tempFile = Files.createTempFile("session-store-turns", ".jsonl");
        try {
            SessionStore store = new SessionStore(tempFile);
            String content = "Paths C:\\\\new and regex \\\\n should stay literal";
            store.appendTurn(new ConversationTurn(1, "phase1", "model", "assistant", content, 123L));

            List<ConversationTurn> turns = store.loadTurns(tempFile);
            assertEquals(1, turns.size());
            assertEquals(content, turns.getFirst().content());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void loadSynthesesPreservesLiteralBackslashEscapes() throws Exception {
        Path tempFile = Files.createTempFile("session-store-synth", ".jsonl");
        try {
            SessionStore store = new SessionStore(tempFile);
            String synthesis = "Keep literal regex tokens like \\\\t and \\\\r";
            store.appendSynthesis(2, synthesis);

            List<String> syntheses = store.loadSyntheses(tempFile);
            assertEquals(List.of(synthesis), syntheses);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}

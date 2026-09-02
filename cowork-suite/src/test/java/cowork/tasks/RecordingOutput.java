package cowork.tasks;

import cowork.context.ProposedChange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * TaskOutput that records every call so tests can assert on what a task showed. Tasks
 * finish on the EDT via SwingWorker, so await() polls for a condition with a timeout.
 */
public final class RecordingOutput implements TaskOutput {

    public record Shown(String title, String body) {}

    private final List<Shown> outputs = Collections.synchronizedList(new ArrayList<>());
    private final List<List<ProposedChange>> proposals = Collections.synchronizedList(new ArrayList<>());
    private final List<String> statuses = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void showOutput(String title, String body) {
        outputs.add(new Shown(title, body));
    }

    @Override
    public void showProposals(List<ProposedChange> proposals) {
        this.proposals.add(List.copyOf(proposals));
    }

    @Override
    public void setStatus(String status) {
        statuses.add(status);
    }

    public List<Shown> outputs() { return List.copyOf(outputs); }
    public List<List<ProposedChange>> proposals() { return List.copyOf(proposals); }
    public List<String> statuses() { return List.copyOf(statuses); }

    public boolean await(Predicate<RecordingOutput> condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test(this)) return true;
            Thread.sleep(20);
        }
        return condition.test(this);
    }
}

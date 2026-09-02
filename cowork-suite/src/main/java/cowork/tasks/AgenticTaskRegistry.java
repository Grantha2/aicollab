package cowork.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available agentic tasks. Tasks are registered at startup and rendered in the
 * Agentic Routines sidebar, grouped by category in registration order.
 */
public class AgenticTaskRegistry {

    private final List<AgenticTask> tasks = new ArrayList<>();

    public void register(AgenticTask task) {
        tasks.add(task);
    }

    public List<AgenticTask> getAllTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public AgenticTask getById(String id) {
        return tasks.stream()
            .filter(t -> t.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public Map<String, List<AgenticTask>> getByCategory() {
        Map<String, List<AgenticTask>> grouped = new LinkedHashMap<>();
        for (AgenticTask task : tasks) {
            grouped.computeIfAbsent(task.getCategory(), k -> new ArrayList<>()).add(task);
        }
        return grouped;
    }
}

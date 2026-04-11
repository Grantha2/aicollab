package collab;

// ============================================================
// AgenticModeTask.java — Abstract base for tasks that drive a
// Claude Managed Agent through the full agent / environment / session
// / streamed-events lifecycle.
//
// WHY IT EXISTS:
// Every managed-agent task repeats the same scaffolding: collect user
// inputs, upload any attached files, create an agent, create an
// environment, create a session, send the first event, stream back
// results. This base class owns the scaffolding so concrete tasks like
// RoomBookingTask only supply the interesting bits (prompt, inputs,
// files, tools).
//
// EXECUTION MODEL:
// execute() hops off the EDT onto a SwingWorker so the API handshake
// and streaming don't freeze the UI. Events flow back to the panel
// via ManagedAgentClient's push-style consumer; the panel itself
// marshals updates onto the EDT.
// ============================================================

import javax.swing.SwingWorker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class AgenticModeTask implements AgenticTask {

    /**
     * System instructions given to the agent at creation time.
     */
    protected abstract String getSystemPrompt();

    /**
     * Prompt the user for task inputs on the EDT. Return null to
     * cancel execution. Typical implementations pop a Swing dialog.
     */
    protected abstract Map<String, String> collectInputs(AgenticTaskContext ctx);

    /**
     * Turn the collected inputs into the first user message.
     */
    protected abstract String buildUserMessage(Map<String, String> inputs);

    /**
     * Files to upload before the first event. The returned Files API
     * ids are passed to the first sendEvent call.
     */
    protected List<Path> getInputFiles() {
        return List.of();
    }

    /**
     * Tools the agent is allowed to call. Defaults cover the common
     * bash + file editing use cases; override for task-specific tools.
     */
    protected List<String> getTools() {
        return List.of("bash", "file_edit");
    }

    /**
     * Python packages to install in the sandboxed environment.
     * Override when the task needs extras like pypdf or pandas.
     */
    protected List<String> getEnvironmentPackages() {
        return List.of();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    // ============================================================
    // execute() — Scaffolded lifecycle all managed-agent tasks share.
    //
    // 1. Check the client is configured (config toggle off -> bail).
    // 2. Collect inputs on the EDT (user may cancel).
    // 3. Hop to a SwingWorker for the network work.
    // 4. Upload input files, create agent/env/session, send first
    //    event, pump every streamed AgentEvent into the panel.
    // ============================================================
    @Override
    public void execute(AgenticTaskContext ctx) {
        ManagedAgentClient client = ctx.managedAgentClient();
        AgenticRoutinesPanel panel = ctx.panel();

        if (client == null || !client.isConfigured()) {
            panel.showFunctionOutput(getName(),
                    "Managed Agents client is not configured. "
                    + "Set managed.agents.key and managed.agents.url in config.properties.");
            return;
        }

        // Collect inputs on the EDT before we go async.
        Map<String, String> inputs = collectInputs(ctx);
        if (inputs == null) {
            panel.setStatus(getName() + " cancelled.");
            return;
        }

        panel.showLoading("Running " + getName() + "\u2026");

        new SwingWorker<Void, AgentEvent>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Upload any attached files first — the agent references them by id.
                    java.util.List<String> fileIds = new java.util.ArrayList<>();
                    for (Path p : getInputFiles()) {
                        if (p == null || !Files.exists(p)) continue;
                        String id = client.uploadFile(p, guessMime(p));
                        if (id != null) fileIds.add(id);
                    }

                    String agentId = client.createAgent(getSystemPrompt(), getTools());
                    if (agentId == null) {
                        publish(AgentEvent.status("Failed to create agent."));
                        return null;
                    }

                    String envId = client.createEnvironment(getEnvironmentPackages());
                    if (envId == null) {
                        publish(AgentEvent.status("Failed to create environment."));
                        return null;
                    }

                    String sessionId = client.createSession(agentId, envId);
                    if (sessionId == null) {
                        publish(AgentEvent.status("Failed to create session."));
                        return null;
                    }

                    // Stash the session id on the panel so follow-up turns
                    // (e.g. room confirmation) can be sent against the same
                    // conversation without starting over.
                    panel.setActiveAgentSession(sessionId, client, AgenticModeTask.this);

                    String userMessage = buildUserMessage(inputs);
                    client.sendEvent(sessionId, userMessage, fileIds, this::publish);

                } catch (Exception e) {
                    publish(AgentEvent.status("[Task ERROR] " + e.getMessage()));
                }
                return null;
            }

            @Override
            protected void process(java.util.List<AgentEvent> chunks) {
                for (AgentEvent ev : chunks) {
                    panel.appendAgentEvent(ev);
                }
            }

            @Override
            protected void done() {
                panel.setStatus(getName() + " finished.");
            }
        }.execute();
    }

    /**
     * Best-effort MIME detection by file extension.
     * Java's Files.probeContentType is environment-dependent, so we fall
     * back to a short whitelist for the types we actually ship.
     */
    protected static String guessMime(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf"))  return "application/pdf";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt"))  return "text/plain";
        if (name.endsWith(".md"))   return "text/markdown";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".csv"))  return "text/csv";
        try {
            String probed = Files.probeContentType(p);
            if (probed != null) return probed;
        } catch (Exception ignore) {
            // fall through
        }
        return "application/octet-stream";
    }
}

package collab;

// Owns the on-disk OrganizationContext and exposes read/merge hooks.
// Kept as a thin layer so MainGui and PromptBuilder don't reach into
// the file directly.
public class ContextController {

    private final OrganizationContext org;

    public ContextController() {
        this.org = OrganizationContext.load();
    }

    public ContextController(OrganizationContext org) {
        this.org = org;
    }

    public OrganizationContext organization() {
        return org;
    }

    public String getOrgContextBlock() {
        return org.buildContextBlock();
    }

    public void mergeAndSave(java.util.Map<String, String> updates) {
        if (updates == null) return;
        for (var e : updates.entrySet()) {
            if (e.getValue() == null) continue;
            String before = org.get(e.getKey());
            if (!before.equals(e.getValue())) {
                System.err.println("[ContextController] " + e.getKey() + " updated.");
                org.set(e.getKey(), e.getValue());
            }
        }
        org.save();
    }

    public void save() {
        org.save();
    }
}

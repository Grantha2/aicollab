package cowork.tasks;

import cowork.context.ProposedChange;

import java.util.List;

/**
 * What an agentic task shows the user: a result card, AI-proposed context changes awaiting
 * approval, and a one-line status. Implemented by the Agentic Routines panel; called on the EDT.
 */
public interface TaskOutput {

    void showOutput(String title, String body);

    void showProposals(List<ProposedChange> proposals);

    void setStatus(String status);
}

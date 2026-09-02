package cowork.tasks;

import cowork.context.ProposedChange;

import java.util.List;

/**
 * Sink for everything an agentic task wants the user to see: a result card, AI-proposed
 * context changes awaiting approval, and a one-line status. Tasks depend only on this
 * interface so cowork.tasks never touches cowork.ui; the Agentic Routines panel implements
 * it. Tasks invoke these from the EDT (their SwingWorker.done()).
 */
public interface TaskOutput {

    void showOutput(String title, String body);

    void showProposals(List<ProposedChange> proposals);

    void setStatus(String status);
}

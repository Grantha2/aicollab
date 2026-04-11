# Agentic task templates

Drop PDF/Office templates used by agentic tasks in this directory.

## Expected files

- `room_request.pdf` — blank room-reservation template used by
  `RoomBookingTask`. The agent fills this PDF via `pypdf` inside its
  sandboxed environment, then the filled output streams back through
  the Files API as a downloadable attachment.

If a template is missing at runtime, the task framework
(`AgenticModeTask.execute`) silently skips the upload. The task still
runs — the agent just won't have a template to fill.

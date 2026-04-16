# Computer-use sandbox

The `RoomReservationWorkflow` uses Claude's native computer-use tools
(`computer_20241022`, `bash_20241022`, `text_editor_20241022`) to drive
the UIC EMS page (`emsenterprise.uic.edu/vems/BrowseForSpace.aspx`)
when `room.availability.mode=live`. Those tools need a real browser on
a real display, which lives in a sandbox container — not in the Java
app. This document describes how to run the sandbox and how the Java
side talks to it.

## Architecture

```
   Swing app (Java)                  Sandbox (Docker, external)
  ─────────────────                  ──────────────────────────
  ComputerUseToolProxy   ---HTTP---> POST /v1/computer-use
       ^                                    │
       │                                    ▼
   ToolResult   <------ JSON ------   {"output":"...","image":"..."}
                                        │
                                        ▼
                               Xvfb + Chromium + key/click driver
                                        │
                                        ▼
                         UIC EMS BrowseForSpace.aspx (live)
```

- The Java side knows nothing about X11, browsers, or screenshots.
- The sandbox knows nothing about aicollab, debates, or profile sets.
- The only contract is the HTTP endpoint and the JSON shape on each side.

## Quick start (recommended)

```
docker compose up -d computer-use-sandbox
```

This starts Anthropic's reference computer-use sandbox image (or an
equivalent image that exposes a `/v1/computer-use` HTTP endpoint).
Visit `http://localhost:6080` to see the sandbox desktop in your
browser; this is handy for rehearsing the demo and verifying that
the sandbox is clicking through the EMS page correctly.

## Configuration

The Java side reads one property from `config.properties`:

```
# Override if the sandbox is bound to a different host/port, or if
# you point the container at a different container orchestrator.
computer.use.sandbox.url=http://localhost:9000/v1/computer-use

# Fixture mode is deterministic (assets/fixtures/room_availability.html)
# and doesn't require the sandbox. Use "live" only when the sandbox
# is running and you want Claude to drive the real EMS page.
room.availability.mode=fixture
```

Both keys are optional. Defaults (`localhost:9000` and `fixture`) mean
a freshly-cloned checkout can run the workflow without any extra
configuration — the fixture mode works entirely offline.

## Fallback behaviour

When the sandbox URL is unreachable, `ComputerUseToolProxy.dispatch`
returns `ToolResult.error` with a clear message. Claude sees the error
as tool output and can:

- Apologise to the user and suggest fixture mode.
- Produce a partial result based on what it already knows.
- Ask clarifying questions instead of guessing.

The debate never aborts because the sandbox is down.

## Why not implement computer-use in the Java app?

Running a browser on a Swing app's host process would add:
- Xvfb or equivalent virtual framebuffer (Linux) / a real display
  (Mac/Windows).
- A headless Chromium + driver package.
- A keystroke/click automation library (Robot suffices but is brittle).
- Extra failure modes tangled into the app's crash domain.

Keeping it in a separate container means the student team can explain
one Java class (`ComputerUseToolProxy`) whose job is "JSON in, JSON
out" — not the entire computer-use stack. That is the right split for
a demo where the grade depends on explaining the Java code.

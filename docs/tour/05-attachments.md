# 5. Attachments — how a file rides along on a debate

Shipped in PR #27 as "Phase A". The user drops a file, every panelist
sees it on its Phase 1 turn, the debate proceeds as before.

## The data model

```
ContextAttachment (sealed interface)
    └── FileAttachment (final class, Gson-friendly)
            - localPath, mimeType, displayName
```

`ContextAttachment.java:30` uses `permits FileAttachment` to keep the
set closed. Two future siblings are planned in later phases
(`RagCorpusAttachment`, `McpServerAttachment`) and the sealed hierarchy
is how we force every client's switch-on-attachment to stay exhaustive
when those arrive.

## The round-trip

1. **User attaches.** `AttachmentsDialog` (reached from the debate
   toolbar) adds `FileAttachment` instances to the active
   `ProfileSet.attachments` (`ProfileSet.java:22, 102-108`). Attachments
   persist in `profile-set.json` so reopening the app keeps them
   attached — but provider-specific file IDs do NOT persist (see
   [persistence](04-persistence.md)).

2. **Maestro forwards.** Before each debate, `MainGui.rebuildMaestro`
   calls `Maestro.setAttachments(profileSet.getAttachments())`. Phase 1
   turns include the list (`Maestro.java:226-230`). Phase 2/3 turns do
   NOT — they rely on provider-retained or client-replayed state.

3. **Client uploads lazily.** On a Phase 1 turn with attachments, each
   client calls `FileUploader.ensureUploaded(provider, file, apiKey,
   http)` (`FileUploader.java:100-143`). First call uploads; subsequent
   calls for the same `(provider, path, apiKey)` tuple return the
   cached ID. Upload failures return `null` and the debate continues
   without that attachment.

4. **Client realises on the wire.** Each provider encodes attachments
   differently:
   - **Anthropic** → `{"type":"document","source":{"type":"file","file_id":"…"}}`
     prepended to the user-message content array. Because Anthropic uses
     client-side replay, the document blocks are pinned in
     `ConversationState` so they stay visible on Phase 2/3 turns.
   - **OpenAI** → `{"type":"input_file","file_id":"…"}` in the Responses
     API `input` array. Only on the first turn — chained turns use
     `previous_response_id`, and OpenAI retains the file reference
     server-side.
   - **Gemini** → `fileData: { mimeType, fileUri }` part on the first
     turn's input. The Interactions API retains attachments across
     chained turns, so Phase 2/3 only send the new user message.

## The upload dialects, one paragraph each

`FileUploader` owns all three multipart bodies so the clients stay
focused on request-building:

- **Anthropic** `/v1/files` — header `anthropic-beta: files-api-2025-04-14`,
  single-field multipart, response has a top-level `id`.
- **OpenAI** `/v1/files` — header `Authorization: Bearer …`, two-field
  multipart (`purpose=user_data` + `file`), response has `id`.
- **Gemini** `/upload/v1beta/files?uploadType=multipart` — header
  `x-goog-api-key`, `multipart/related` with a JSON metadata part
  followed by the raw bytes, response has `file.uri`.

Caching is keyed by `(Provider, absolute path, apiKey hash)` so two
app instances with different keys do not collide
(`FileUploader.java:145-148`). 20 MB hard cap per file — above typical
PDFs, below every provider's per-file ceiling.

## Why this design is safe

- **No persisted file IDs.** Stale IDs cannot re-enter the system
  after restart.
- **Upload failures never kill the debate.** A `null` return means
  "skip this attachment on this turn"; other attachments and
  panelists keep working.
- **Phase 1 is the only write-path.** The attachment list is
  immutable-by-convention during a debate, so no race between panelist
  threads modifying what each other sees.

## What changes in PR #30

`AttachmentLibraryStore` lets a user pin reusable files (the
RSO-Facility-Request-Form.pdf, for example) that live across debates.
`PanelistSlot` gains its own `List<ContextAttachment>` so a user can
give the Finance panelist the budget and the Recruitment panelist the
recruitment plan. The merge point is `Maestro` — debate-level +
slot-level are unioned before each Phase 1 request.

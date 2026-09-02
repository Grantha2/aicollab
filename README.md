# aicollab → two projects

This repository is the launch pad for two standalone applications that
grew out of the original multi-model debate prototype. Each lives in its
own directory and is meant to become its own GitHub repository.

| Project | Directory | One line |
|---|---|---|
| **Conductor** | [`conductor/`](conductor/) | Walks a non-engineer through the software lifecycle on rails — Idea → Users → Requirements → Design → Plan → Build → Verify → Ship — asking plain-language questions and calling a panel of AI agents (Claude, GPT, Gemini, OpenClaw) only where a second opinion earns its cost. Output: artifacts ready to hand to an engineering agent. |
| **Cowork Suite** | [`cowork-suite/`](cowork-suite/) | A button-driven AI desk for organisational leaders: task templates with forms, scheduled agentic routines, a shared organisational-context store with freshness tracking, and human-approval gates on AI-proposed changes. |

Start with each project's `README.md`, then `docs/CODE_TOUR.md`, then
`FUTURE.md` for where it is going.

**To split these into their own repositories**, follow [`MIGRATION.md`](MIGRATION.md).
It also lists the old branches to delete or archive.

The pre-split codebase is preserved at commit `88b926c`
(tag `archive/full-suite-final` after Step 5b of the migration).

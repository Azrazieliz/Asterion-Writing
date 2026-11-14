# Asterion Writing — progress ledger

2026-11-14 — canonical repository `Azrazieliz/Asterion-Writing` (existing repo was empty when inspected).

## Completed: host foundation

- Authored initial canonical product, integration, model-development and acceptance specifications.
- Implemented Kotlin core for multi-universe hierarchical catalog, stable chapter IDs, canon event history and character-value knowledge boundaries.
- Added real append-only metadata event journal, SHA-256 integrity, explicit truncated-tail backup/recovery, and event replay to durable project catalog/canon.
- Added heuristic reference n-gram fingerprinting, repetition/length gates and mandatory human editorial review finding.
- Added only-one-active-model runtime abstraction with Rounder-staged file verification and native tokenizer context-budget guard.
- Added Nexus-bound draft generation interface (interface only; no invented Nexus implementation), explicit non-automatic publication.
- Executed the actual Kotlin host test script and observed passing assertions. No claims of Android builds or model inference.

## Outstanding (not implemented)

1. Inspect latest production merger source/runtime ownership and existing Rounder/Nexus app-to-app interface definitions on device and repo.
2. Implement shared Android library integration and actual native writer inference adapter reusing available Vulkan/QNN code as appropriate.
3. Test base candidates against device RAM/thermal/tokenization and real mature web/light novel prompts; select on quality.
4. Gather provenance-verified original training/evaluation corpus and demonstrate a trained writing-specialist checkpoint improves on base.
5. Wire model staging receipts from Rounder, story persistence/reading to Nexus, and resource arbitration with Art/Sound/3D.
6. Implement preference analysis of owner-controlled EPUBs and semantic originality/continuity editorial pipeline.
7. Build, install and test on Samsung SM-S948B; never confuse host tests with phone acceptance.

**No existing Asterion code or user models altered. No training, phone APK, or end-to-end generation delivered in this foundation.**

## 2026-11-14 — v0.4 Writer Workspace source integration (newest)

**Implemented in source, not yet declared phone-accepted:**
- Native Android Studio / Library / Models / Review tabs: structured universe/series/volume/chapter inputs, authoring profiles, chapter plans, cast, canon and per-scene goals.
- Local preview manuscript persistence with per-project IDs, safe revision snapshots, approved read-only chapters, deliberate forks and linked next chapters that inherit only explicitly accepted outcome summaries. Nexus remains future authority; no invisible sync claim.
- NarrativeController scene statuses, timeline-order constraints, bounded canon and character-knowledge instructions, structural repetition/length checks and host tests.
- On-phone chapter continuation after interruption, mode-driven worldbuilding/character arcs/volume outlines/continuity critique, selective native AI passage rewriting and human approval before changing the draft.
- Explicit GGUF download pause/resume with persisted transfer identity and Range Content-Range check; saved drafts and previous outputs are not silently overwritten.
- Existing CPU llama.cpp GGUF inference reused, with optional compatible GGUF LoRA adapter import / native attach before the system prompt. No conversion, model acceptance or acceleration implied.
- Human eight-axis model quality assessments plus first-text/character-rate, real available memory, battery and thermal samples; no synthetic token/s claim.
- Rights/provenance-gated LoRA adaptation script with story-universe-separated validation. Actual training, GGUF conversion, quality evaluation and on-device LoRA proof remain outstanding.
- SAF manuscript export and local Android model/adapter import; no invented Nexus/Rounder service.
- CI v0.4 builds from `main` through the pre-existing Android workflow, with host-core and Python script syntax validation.

**Still blocked / not delivered:**
- An actual accepted writing-specialist checkpoint and controlled blind quality comparison; there is no training dataset or suitable training GPU committed.
- Native QNN/Vulkan GGUF backend; the inherited text runtime is CPU only.
- Authenticated Nexus transactional draft/publish API and full Rounder/TeraBox staging API (not present in inspected available repositories); user-authorized SAF/manual import/export remain the functional route.
- True host-level merged Art + Sound + 3D app integration and resource arbitration; Sound's current repository contains a specification, not an Android module.
- Durable background inference service / automatic process-death resume at exact token position, automatic semantic continuity editing and full multi-volume on-device literary acceptance.
- Installed and device-verified APK is an additional gate after CI. Do not call this delivered solely from a source commit.

**Source entrypoints:** `core/NarrativeController.kt`, `app/.../WriterWorkspaceStore.kt`, `app/.../MainActivity.kt`, `scripts/writer_specialist_pipeline.py`, `docs/ROADMAP_TO_PRODUCTION.md`.

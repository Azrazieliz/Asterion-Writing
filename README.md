# Asterion Writer — original-fiction production module

**Current source:** Android companion `com.asterion.writing.bench`, version `0.4.0-writing-workspace`.
This is an actual local-model fiction authoring preview, **not** the finished merged Asterion production app. The approved target remains a Writing module in the combined Art + Sound + 3D creator application.

## What the current preview implements

- Native Android **Studio**, **Library**, **Models**, **Review** interface, with an original premise, authoring profiles, characters, canon, series/volumes, chapter plan and scene objective.
- Creative modes for chapter prose, provisional worldbuilding, character arcs, volume outline and continuity critique. Changes only enter the project after explicit review.
- Local CPU GGUF inference using the existing pinned Asterion Art llama.cpp module; real streaming prose, native sampler controls, multiple segments and incremental checkpoints.
- One active base model, optional matching local GGUF LoRA adapter, import through Android's system document picker, GGUF downloads with explicit pause/resume and checksum when the upstream digest is available.
- Separate independent projects, chapter revisions, non-destructive forks, editorial locks, linked next chapters with manually accepted consequence summaries, interruption recovery and targeted AI excerpt replacement with confirm/discard.
- Eight human-scored prose quality dimensions and real device memory, battery and thermal samples; source metrics do not invent native tokens/s.
- Manual Markdown export through SAF and Android share intent; model weights, personal stories, training corpus and credentials are never checked into Git.

Core Kotlin foundations: stable story identities, journal replay, canon chronology/character knowledge, narrative scene director, similarity/repetition heuristics and native runtime contracts. Run `bash scripts/test-core.sh` for host tests.

The Android CI in `.github/workflows/android.yml` builds an arm64 APK and uploads the **Asterion-Writing-v0.4.0-Workspace-APK** artifact after host/Python checks. The companion package is separate from the older `com.asterion.writing.preview` v0.1 installation; do not uninstall it to reclaim private model files.

## Still unimplemented or unverified

- A trained, approved specialist model; `scripts/writer_specialist_pipeline.py` prepares training/validation but has not produced a trained model or demonstrated improvement.
- Actual Vulkan/Qualcomm QNN acceleration for fiction GGUF (the inherited text engine is CPU only); do not confuse Art's image acceleration with text offload.
- An authenticated Nexus publication/storage API, EPUB-driven preference service or Rounder/TeraBox staging receipt service. Current supported interop is local file import, document export and Android sharing; the relevant native APIs must be verified before direct wiring.
- The host-level merged Art + Sound + 3D application, shared resource manager and specialist asset handoffs. Other components must expose compatible interfaces before a safe merge.
- Exact mid-token restart, fully unattended generation after process death, semantic originality proof and sustained dozens-of-chapters / multi-volume owner acceptance.
- Installation, phone-run and literary-quality verification of the latest v0.4 source are separate evidence gates from successful CI packaging.

Read `docs/SPEC_V1.md`, `docs/PRODUCT_ARCHITECTURE_V2.md`, `docs/ROADMAP_TO_PRODUCTION.md`, `docs/DEVICE_TEST_PROTOCOL.md` and `docs/PROGRESS.md`.

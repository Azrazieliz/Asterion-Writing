# Asterion Writer — real-story preview to integrated production (2026-11-14)

## Current state and truth labels

Repository: `Azrazieliz/Asterion-Writing`. The Android companion source is `com.asterion.writing.bench`, version 0.3.0 real-story preview. Reuses Asterion Art's CPU GGUF native inference module; Vulkan/QNN text offload remains unverified. Source is committed; build, installation, phone execution and literary acceptance are **independent gates**, not implied by a commit.

Prior v0.1 `com.asterion.writing.preview` uses a distinct package identity and signing certificate. Its app-private Stheno GGUF and partially downloaded Magnum cannot be automatically accessed by companion v0.3. Do not erase v0.1 data or promise cross-package resume. Resume in v0.3 works on partial files created by the companion itself, or accessible external imports.

## Next test should be real writing, not isolated prompt quality

1. Make a private original universe/series, choose a chapter objective, characters, relationships, canon constraints and scene goals.
2. Generate multi-scene prose from **one real local GGUF**, check the model's actual context/token budget and save after each scene.
3. Pause a model transfer, recreate the activity, tap **Resume interrupted download**, verify the same bytes and SHA without restarting when server supports valid HTTP Range.
4. Stop during a scene, relaunch and **Continue chapter from saved text**. This appends a new scene with bounded preceding prose; it is **not exact mid-token/model-state restoration**.
5. Review the full chapter: credible decisions, natural dialogue, scene outcomes, character voice, prose style, originality, repetition and coherence. Measure time to first text, characters/second, real device thermals, memory, load times and stability. Compare models at matched output lengths.
6. Continue across multiple chapters with persistent approved story state. **This is not yet delivered** by pilot; do not claim full continuity based on recent-text prompting.
7. Never auto-publish to Nexus.

## Remaining production gates

| Milestone | Actual status | Acceptance gate |
| --- | --- | --- |
| Kotlin hierarchical catalog / canon / checksum journal / structural quality | Host foundation implemented and host-tested | Ensure Android storage flows preserve IDs, reveal boundaries and reviewed canon |
| Single-model CPU GGUF test app | Source implemented; original baseline CI built | Real installed, long-session device generation and resource measurement |
| Resumable model transfers, UI restart, chapter continuation | v0.3 source added; not yet phone-accepted | Pause/restart/resume, byte integrity, activity/process death, no duplicate downloads |
| Actual professional project/series/volume/chapter UI | Pilot has lightweight structured fields only | Multi-project creation, chapter tree, scenes, character editor, revision history, editable prose, accessibility |
| Long-form narrative controller | Pilot supports multi-segment prose, not genuine arc tracking | State updates per scene, causal planner, time/knowledge checks, dozens-of-chapters continuity |
| Production model | No trained/accepted specialist | Baseline two unrelated families, adapted writer-specific 0.5–3B candidates when measured feasible, blind quality comparisons |
| Native accelerated inference | Prose backend currently CPU; Vulkan/QNN unverified | Real compatible operator graph, profiler evidence, sustained tokens/sec, no hidden CPU fallback |
| Rounder + TeraBox | HF/app-private download + manual document import only | Authenticated verified local staging receipt, cache reuse, model lifecycle |
| Nexus persistence/reading | Explicit draft share only; actual Nexus adapter not wired | Idempotent save, review/approve, publish, versioned canon and EPUB preference analysis |
| Originality, literary quality, revisions | Heuristic text-gate only | Editorial human acceptance plus semantic/plot-consistency checks and controlled rewrites |
| Merged Asterion integration | Not wired | Shared Art/Sound/3D model arbitration, optional asset handoff, signed upgrade and device acceptance |
| Multi-volume reliability | Not demonstrated | Completed chapters, full volume workflow, interruption/restart recovery, no loss/duplication, owner acceptance |

## Core design decisions

- Writer is **not** a second reader, archive, online AI service or separate production application. Nexus owns books and reading; Rounder handles verified model staging; merged creation app owns app lifecycle.
- Writing is **project and scene based**, with model parameters in advanced settings. Keep the benchmark harness as a diagnostic panel, not the production primary surface.
- Store accepted decisions, chronology, character knowledge, current scene targets and last accepted chapter separately from recent prose; retrieval should select only a bounded necessary canon slice for each context window.
- Never treat a completion's token cap as a chapter/volume cap. A chapter is composed of accepted scenes; a volume is composed of accepted chapters. Save transactional checkpoints before switching models.
- Preserve the previous model/draft, package signing and archived files. Verify before promising an install, compatible resume or finished app.

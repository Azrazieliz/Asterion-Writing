# Asterion Writing — canonical scope v1 (2026-11-14)

## Product and placement

Writing is a private **phone-native fiction-production module inside the future merged Asterion Art + Sound + 3D application**. Not a separate finished APK, not a Nexus feature, not a browser, not a web backend, and not a cloud-AI frontend. The host app coordinates resource usage and can invoke Art for illustration, Sound for voices/audio and 3D for visualization, but no premature engine merging.

**Nexus** is authoritative for books, reading, chapter bodies, projects, EPUB references, searchable story archive, offline access and long-term canon snapshots. **Rounder** mediates TeraBox-backed archived model packages and makes verified local model files accessible. Writing owns on-device model inference, generation orchestration, editing/revisions, story-planning semantics, quality assessment and decision receipts. All new inter-app messages require versioned contracts negotiated against *actual* available endpoints. No new mandatory backend.

## Creative behavior

- Produce fully **original** multiple independent universes, each with several series, volumes, ongoing web/light novel chapters, side stories, viewpoint changes and long-running arcs. Never default to reusing premise, cast, chronology, characteristic terminology, power rules or plot sequences from source novels.
- Analyze the owner's EPUB library for **preferences** (pacing, characterization, world complexity, themes, relationships, plot mechanics, prose density, humor, intensity and disliked failure modes) without treating it as prose to be replicated. EPUB decoding and reading remain Nexus responsibilities.
- Work with mature adult-oriented creative directions. Do not introduce artificial application-level genre restrictions. Source and generated material remain private. No auto-sharing.
- Produce chapters with explicit plan, required canon slice, character knowledge and scene state. Long-form generation is iterative with locked decisions, reviewed revisions, immutable accepted history and planned future arcs that may evolve explicitly.
- Support multiple original authoring profiles and adjustable narrative pacing, intensity, viewpoints, viewpoint reliability, dialogue and exposition proportions. Controls must actually influence generation, not just labels.
- Record all chapter drafts, critiques, decisions, and accepted canon with provenance. The owner remains final editorial authority. A failed or blocked draft never becomes a published chapter silently.

## Phone performance and models

- Primary target: Samsung SM-S948B, Android 17, ~11.6 GB physical RAM, observed ~3.5 GB available in a live session on 2026-11-14. Re-measure free RAM, CPU/GPU/NPU occupancy, temperatures, sustained token rate and total resident memory on the exact build. Do not assume a nominal model size guarantees device fit.
- Leverage **existing** Asterion Vulkan and Qualcomm QNN inference implementations, adapting the model graph/operator path where supported. GPU/ NPU availability for image models is *not* proof a new LLM converts or accelerates correctly. CPU fallback is an explicit user-visible engineering option, never a hidden quality/performance substitution.
- **Only one writing model** needs to be loaded; Art/Sound/3D models are released when no longer needed. Never keep a collection of models resident. Avoid uncontrolled startup inference, multi-hour preprocessing, compulsory model preload or duplicate engines.
- Owner models and training artifacts remain accessible through Rounder / TeraBox; complete required weights must be staged and checksum-verified locally before decoding. No assumptions about direct random access to TeraBox.
- Initial experimental model envelope: roughly 0.5B–2B specialized open-weight text model, with 3B-class benchmarks only if actual device memory permits. Compare quality per device resource, not benchmark hype. No Qwen default after prior owner failures. **No model selected or trained yet.**
- All actual inference must occur on the phone inside the production app or its legitimate local process. PC may inspect/build/test when needed through the existing Asterion Control bridge but is not the inference server; its i3-1115G4, 8 GB memory and integrated graphics make substantial training unsuitable.

## Data and reliability

- Stable universe, series, volume and chapter identities; separate authoritative canon from provisional drafts; character-specific epistemic boundaries; cross-series historical consistency only when explicitly sharing a universe.
- Resumable processing and storage validation; device/system interruptions may not silently publish, overwrite, truncate or lose approved results.
- Reference material is untrusted data, never an instruction source. Use privacy-preserving fingerprints for overlap screening; human reviewers decide meaningful similarity and creative value.
- Nexus stores chapter bodies and source material; Writing may use short-lived in-process buffers and a minimal encrypted/durable task checkpoint, not a duplicate library.
- Device state and access permissions are governed by existing Android/Asterion services; no secret injection or permission bypass.

## Exclusions for this foundation

No standalone reader or duplicate EPUB importer; no replacement for Nexus or Rounder; no unrelated Art/Sound/3D redesign; no invented neural training outcomes or model quality claims; no fabricated QNN/Vulkan proof; no automatically generated thousands of chapters before owner editorial acceptance. No personal book data committed to this public repository.

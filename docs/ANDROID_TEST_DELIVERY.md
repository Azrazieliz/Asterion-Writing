# Asterion Writing Android pilot

`com.asterion.writing.preview` is a temporary package for on-phone model comparisons. It is not the merged production application and does not replace Asterion Art, Sound, 3D or Nexus.

## Implemented
- Native Android test UI with editable original fiction brief, canon input, output length, streamed prose, stop and local draft persistence.
- Hugging Face public GGUF repository listing, filename/quantization chooser, app-managed HTTPS download, Range resume, foreground notification, pause, atomic finalize, GGUF signature validation and SHA-256 verification if metadata provides expected LFS digest.
- Local model picker, import from Android document picker (including Rounder-visible local files), and actual llama.cpp inference by reusing pinned Asterion Art `brain` source and exact llama.cpp v0.7.0 checkout, not a fake generation service.
- Only one active model. Hard available-RAM check. Limited to 4096 native context tokens for pilot. Native sampler temperature patched to 0.95 in temporary checkout (not in Art repository).
- Existing foundation QualityGate blocks short/repeated drafts; it does not certify narrative originality or high-quality writing.

## Unverified/not yet implemented
- Native Vulkan/Qualcomm QNN backend **for GGUF prose writing**: the inherited Asterion Art brain module currently uses CPU GGUF. Art's image QNN/RealSR Vulkan functionality is not a GGUF backend. Do not claim GPU/NPU offload without device evidence.
- Rounder direct cloud model streaming: Rounder can expose staged models to Android's document picker if configured, but there is no authenticated API contract implemented here. Downloaded model files are app-private until shared through the production system.
- Automatic Nexus publication, library, reader, EPUB ingestion; no independent reader has been created.
- On-device inference acceptance with downloaded GGUF; complete and verify this before treating writing quality or generation speed as satisfactory.
- Cloud model hosting or remote inference is never used. Hugging Face is download-only.

## Security/limitations
- Public repositories only; gated/private HF requires authorization not implemented here.
- The downloads manager requires room for the entire active GGUF on the phone, not just TeraBox cloud storage.
- Android 15+ imposes time restrictions on dataSync foreground service; partial downloads remain resumable by starting them again.
- Download redirects and source metadata are from public Hugging Face; users must review model licenses and model cards.
- A model weight's SHA is checked against HF metadata when provided; when not provided, only the local checksum is known. Do not claim remote verification in that case.
- Stop generation works through coroutine cancellation at a token boundary. Keep app foreground while generating; durable background inference not yet implemented.
- For source audit, checkout is pinned to Asterion Art commit 0a1129fd9a03d1e3059a26c0e42eefc3244511c3 and llama.cpp v0.7.0 SHA 620d2225cc436ab0b001e3200e590e3b286ef9ab.

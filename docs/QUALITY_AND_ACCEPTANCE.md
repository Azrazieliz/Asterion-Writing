# Foundation gates and evidence (2026-11-14)

## Implemented host-verifiable checks

- Universes, series, volumes and chapter IDs isolated and ordered; duplicates rejected.
- Canon cannot be silently overwritten; transitions require expected previous value and reason; secrets are known only after explicit reveal of the correct value; later changes are not automatically known.
- Durable journal checks sequence, record length and SHA-256; corrupted records fail; incomplete final record requires explicit backup and recovery.
- Stable data survives reconstruction; Nexus content references cannot silently be replaced.
- Drafts are stored as drafts, quality findings accompany them and publication remains external and explicit.
- Repeated substantial paragraphs, too-short drafts and exact 10-word reference overlap are blocked in the baseline policy. A semantic/human editorial review warning is *always* present.
- Rounder cache must return an existing file; model SHA must match; native backend compatibility checked; native tokenizer must certify prompt+generation fits model context. The model session is single-resident and closeable.

## NOT yet verified or implemented

- Android module build and production-app host injection.
- Native writing-model graph and true Vulkan/QNN execution / trained checkpoint.
- Direct Rounder API and TeraBox staging implementation.
- Nexus API adapter, transactional publication and real EPUB-derived preference engine.
- Persistent real model generation, high-quality long-form output, semantic originality assessment and multi-volume literary stress testing.
- Art/Sound/3D creative output handoffs and phone APK acceptance.

## Guardrails on future claims

Use distinct states: `source-implemented`, `host-tested`, `APK-built`, `installed`, `phone-executed`, `quality-accepted`. A fake native-port unit test **does not** prove on-device LLM inference. Heuristic phrase overlap is neither proof of no copying nor a substitute for human comparison. Accept no model based solely on benchmark scores or high-level architecture documents.

## Foundation test

`bash scripts/test-core.sh` — compile with `kotlinc`; run actual Kotlin tests. Requires no LLM, Android SDK, internet or owner data. Any nonzero result blocks release.

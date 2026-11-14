# Asterion Writing — foundation

**Status: Kotlin core implemented and host-tested; no trained fiction model, Android integration, Nexus/ Rounder connection or phone generation has been proven.**

Asterion Writing is the **fiction-authoring feature** for the future merged Android Asterion production app (Art + Sound + 3D + Writing). It is **not** another reader, another backend, or part of Nexus. Models execute on the Samsung through compatible native Vulkan or Qualcomm QNN runtimes. The existing Asterion Rounder / TeraBox stack stores and stages checkpoint files; **Nexus owns reading, story text, EPUBs and persistent library delivery**.

This repository contains only standalone integration contracts, device-independent deterministic Kotlin core, test fixtures, and engineering specifications. Private books, user preference records, generated fiction, user credentials and model weights must never be committed.

## Foundation implemented

- Multi-universe → series → volume → chapter identities with stable IDs and relationship validation.
- Explicit canon establishment, reasoned transitions, and character-specific knowledge/reveal boundaries. A character does not automatically know later revisions of facts.
- Durable checksum-protected, sequenced append-only metadata journal with explicit backup before recovering a truncated tail; event replay reconstructs catalog and canon.
- Normalized substantial-paragraph repetition and exact reference n-gram overlap checks; the reference index stores **only SHA-256 hashes**, not EPUB excerpts. These checks are heuristics, not proof of originality or literary quality.
- Single-model session contract; checksum-verifies Rounder-staged model bytes, measures available device RAM via injected provider, selects only a supported backend, enforces **native tokenizer** context budget, and unloads inactive models. The native backend remains an integration requirement.
- Draft coordinator produces a **reviewable Nexus draft**, never an automatically published chapter, and retains quality findings.

## Verify on a development machine

Run `bash scripts/test-core.sh` with `kotlinc` and the Kotlin runner installed. No cloud access or dependencies are needed for this host test. The test covers journal recovery, checksum rejection, branching catalogs, canon, knowledge, overlap and fake-port model lifecycle. The fake port is for contract tests only, **not a production writing model**.

## Scope and acceptance

Read [docs/SPEC_V1.md](docs/SPEC_V1.md), [docs/INTEGRATION_CONTRACT.md](docs/INTEGRATION_CONTRACT.md), [docs/MODEL_DEVELOPMENT.md](docs/MODEL_DEVELOPMENT.md), [docs/QUALITY_AND_ACCEPTANCE.md](docs/QUALITY_AND_ACCEPTANCE.md), and [docs/PROGRESS.md](docs/PROGRESS.md). Contracts are deliberately incomplete until matched to the latest owner-approved Art/Nexus/Rounder interfaces; avoid replacing their implementations or inventing API support.

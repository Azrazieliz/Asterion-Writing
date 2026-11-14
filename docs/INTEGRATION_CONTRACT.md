# Integration contracts — v1 intent, not a claimed live connection

## External authorities

| Authority | Owns | Writing requires | Verification |
|---|---|---|---|
| Merged Asterion creation app | Android entry point, lifecycle, resource arbitration, UI | module initialization, mode selection, controlled load/unload, approved media handoff | NOT WIRED |
| Existing Vulkan/QNN native inference | Actual native model execution | supports(model, backend), load(weights), exact countTokens, generate, close, telemetry | NOT WIRED TO WRITING |
| Rounder + TeraBox | Model archive and access | availableLocalModel(modelId), requestStaging(modelId), local file receipt, checksum | INTERFACE ONLY |
| Nexus | Private books, reading, generated story contents and canon exchange | saveDraft, publishApprovedChapter, catalog/EPUB preference signal reads, immutable revision IDs | INTERFACE ONLY |
| Art / Sound / 3D | Specialist creative production | versioned optional illustration, voice, visual scene requests | NOT WIRED |

No new server, cloud endpoint, reader, duplicated model download manager, or remote AI API. Model archives may be in TeraBox but native execution is entirely local once weights are staged.

## Core API already implemented

- `StoryCatalog`: put/get universe, series, volume, chapter, chapter content link with no silent replacement.
- `CanonLedger`: establish, change with expected prior state and narrative cause, reveal a **specific current value** to a character, query historical values and knowledge.
- `ProjectJournal` and `DurableProjects`: checksum-protected event log, replayable catalog/canon, intentional explicit truncated-tail recovery with backup.
- `QualityGate` and `ReferenceShingleIndex`: deterministic short/repetition/exact-shingle findings and mandatory editorial review result.
- `SingleModelHost`: one resident model, supplied memory metric, Rounder cache, checksum, supported backend, tokenizer-based context limit, close.
- `DraftEngine`: form a chapter brief, generate through injected native writer, score with deterministic gate, persist a Nexus draft with quality findings. Never invokes publication.

## Integration rules

1. Inspect *current* Android production source, Nexus interface, Rounder implementation and native inference entrypoints before adopting/refactoring any shared interface. This spec is a target contract; the existing application contracts win on overlapping ownership.
2. Introduce an explicit `asterion.writing.*.v1` schema with fixed enums, idempotency key, input SHA, model checksum, runtime/backend, per-job status, cancellation and durable receipt. Reject unknown breaking versions rather than silently guessing.
3. Before model load, request Rounder staging and wait for the **verified local file receipt**. Never decode directly from an unverified network stream or assume a TeraBox operation succeeded because a handoff succeeded.
4. Coordinate model load/unload with Art/Sound/3D through the host's actual resource manager. Do not assume all specialized models fit concurrently; do not instantiate a second native LLM stack without justification.
5. Nexus is the only production repository for chapter content and reading state. Draft storage receipts and approval transitions must be idempotent. No auto-publication.
6. Source EPUB content may be inspected through Nexus-derived analysis with explicit file coverage and parse statistics. Never send full original books to the model by concatenation or pretend an LLM can fit millions of words in context.
7. Any integration must have a host-level device test demonstrating actual Android execution and correct bytes; source compilation alone is insufficient.

## Known foundation limitations

Current `ProjectJournal` is single-writer metadata-only and replays its log when updating. It is suitable for the foundation, not a high-throughput text corpus. Production integration should use Nexus's durable storage/transaction contracts instead of installing this log as a second library. `ReferenceShingleIndex` is an in-memory exact-hash heuristic; it is not semantic originality detection and cannot replace substantive editorial screening.

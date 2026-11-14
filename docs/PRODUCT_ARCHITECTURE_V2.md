# Asterion Writing — canonical creation architecture (2026-11-14)

## Production goal
A native feature of the future merged Asterion Art + Sound + 3D Foundry + Writing Android app. The feature is **not** a conversational prompt interface. It guides authoring through structured creation, editing, and revision of original web/light novels: universe -> series -> volumes -> chapters -> scenes. Modes include premises, character arcs, world rules, timeline, volume outline, narrative drafting, rewriting, and continuity review. The user edits goals, preferences and story records rather than engineering long prompts.

## Ownership boundaries
- **Writer:** on-device fiction model inference, scene planning, generation, revisions, tests, editorial gates.
- **Nexus:** story archive, persistent canonical text, EPUB import/export, reading and retrieval. Nexus does not run Writer inference; the test APK keeps drafts local until an approved integration exists.
- **Rounder + TeraBox:** existing model availability / archive / local staging. No duplicate cloud model backend; weights must be locally readable during active inference.
- **Art / Sound / 3D:** same future merged app; only load the necessary module/models for the selected task.
- **PC:** development/build aid only, no phone inference dependency. Do not imply remote inference, simultaneous 15-model loading, or that image QNN equals text QNN.

## Efficient testing vs. product authoring
The temporary Writing Test APK *can* expose a raw brief, sampler controls, named test cases, and model selection. It must not dictate the production UI. Every test uses a repeatable brief, same sampling profile, prompt context and length; changes are recorded with model + quantization + generation stats. Tests are sequential (one loaded model at a time). Baseline CPU GGUF backend is real; prose QNN/Vulkan offload is unverified, not silently claimed.

## Long-form generation
A maximum-prediction-token call is **not** a chapter/volume cap. Draft chapters in multiple scene-sized segments, persist each accepted checkpoint, and continue from bounded recent text + authoritative canon/scene objective. Keep separate scene/character/timeline state, track contradictions, repetition, consequences and revision. Explicitly publish to Nexus only after user acceptance. A volume is a sequence of accepted chapters, never one huge model completion. Reliable automated chapter/volume production remains a separate acceptance milestone; the pilot's experimental multi-segment mode is NOT canon management.

## Literary quality gate
Assess meaningful original story mechanics, character psychology, dialogue, social effects, scene consequence, stylistic distinction, repetitive prose, and follow-through. Local structural checks are heuristic only. A mature-content capability test says nothing about prose quality. Do not train on poor generated fiction as positive examples. Do not commit private story drafts or copyrighted reference text.

## Acceptance
Keep evidence states separate: source committed -> CI compiled -> APK packaged -> installed -> model loaded -> generated -> qualitatively accepted. No default success claims for sampling parameters that are not wired to the native engine, and no GPU/NPU claim without real device profiling.

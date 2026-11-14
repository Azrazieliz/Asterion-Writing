# Writer-specific model development and on-device acceptance strategy

## Non-negotiable objective

Train/adapt a genuinely differentiated **writing specialist**, not just an unchanged Qwen/Gemma chat model with a long instruction prompt. Strong chapter prose, natural dialogue, psychology, consequences, causal arcs, continuity, originality and mature adult-oriented literary competence matter more than synthetic benchmark rankings or raw tokens per second.

## Candidate selection

Start by inspecting **actual locally installed** models, their original licenses/formats/tokenizers, native QNN/Vulkan implementation and Rounder cache. Prefer small open-weight models that can be fine-tuned and deployed without rewriting the inference stack. Test at least two unrelated base families. 0.5B–2B is an initial **experiment range**, not final mandate; 3B candidates only if measured free memory and acceptable long-session speed allow. Do not assume QNN conversion works for any architecture until compiled and run.

## Data and training

- Personal library provides **taste signals, coverage reports and negative constraints**, not a verbatim prose cloning target. No copyrighted EPUB text in repository or distributable checkpoints. Guard against excessive reconstruction or near-copying. Never confuse an embedding or preference profile with an actual trained checkpoint.
- Build high-quality, legitimately usable narrative training material with distinct annotation: setting setup, character state, voice consistency, scene causality, pacing, revision directions, intentional mature themes and multichapter constraints. Include hard failures: empty dialogue, contradictions, illogical decisions, repetitive filler, power inflation, copied reference beats.
- Validate corpus provenance and multilingual handling as desired. Split by **story/universe** into train/validation/held-out sets; never leak paraphrased continuations of held-out plots into training.
- Train LoRA/QLoRA or another demonstrably effective adaptation on compatible hardware, measure change against the frozen untuned base, then merge/quantize only after success. The current laptop is insufficient for assumed multi-billion-parameter fine-tuning; phone-side training availability is **unproven**, and inference acceleration must not be described as training acceleration.
- If native on-device training cannot be implemented at the required quality and reasonable budget, report the limitation rather than claiming that prompt engineering constitutes fine-tuning. No external operator or cloud inference is part of the production app.

## Test protocol

1. Baseline models on the same original fiction briefs and blind reviewer scoring (prose, subtext, characterization, causality, world logic, originality and creative range).
2. Compare trained specialist to the exact untuned base and at least one alternative at **matched output length**, with same temperature/runtime/quantization budget.
3. Stress multichapter continuity over dozens of chapters: state transitions, timeline, contradictory descriptions, incomplete arcs, side characters, earlier secrets and knowledge boundaries.
4. Measure exact tokenizer context consumption, prefill latency, time-to-first-token, sustained tokens/s, allocated/peak RAM, backend actually used, shutdown/release, thermal and battery impact on the owner phone.
5. End-to-end with Nexus/Rounder receipts: stage archived weights, verify SHA, load, generate, interrupt/resume, review, save draft, explicit human approve, publish and read in Nexus.
6. Mature creative briefs receive the same qualitative evaluation as other genres. No silent removal/softening of owner-selected legal adult material by the app's own policy layers.

## Acceptance principle

The selected model is accepted only when the owner prefers real outputs to the baselines and phone stability/latency are acceptable. First-run preprocessing must not take hours. No fixed performance or quality claims are made before measured on-device tests.

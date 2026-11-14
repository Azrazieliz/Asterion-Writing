# Repeatable fiction model trials

Baseline first: Stheno 8B Q2_K generated on Samsung, but prose was generic and repetitive. Magnum v2 4B Q4_K_M downloading; no performance or quality result yet.

**Standard benchmark brief:**
Write an original fantasy opening for adults (400–600 words). Begin with an irreversible choice by two morally complicated characters. Invent one unusual supernatural rule with immediate social consequences, reveal the stakes through action and natural dialogue, and avoid generic fantasy names, exposition, prophecies, and repetitive sentences. Write only prose.

Run the identical brief once per model with 4,096 context, 2,048 prediction tokens, temperature 0.80, top-p 0.90, min-p 0.05, repetition penalty 1.12, seed 42, unless source compatibility makes one setting impossible. Save the actual settings, file hash, generation elapsed time, time to first emitted text, words, characters and heuristic findings. Verify actual backend separately; current Android native GGUF uses CPU. Order: Magnum 4B Q4, Creative Writer 3B Q4, then Lumimaid 8B Q4 if loading is safe. Don't start a second large model download while one is still running.

Model performance is not inferred from roleplay fine-tune labels, prior user anecdotes, model names, or a prompt's explicitness. Inspect complete generated text and compare against the fixed rubric: originality, distinctive world, credible motives and relationships, natural dialogue, consequences, style, structural progression, repetition, and ability to continue/revise.

Baseline failures must be captured as real metrics, not a fabricated comparison dashboard. Do not label streaming chunks as native token counts; measure displayed characters/s unless native token count is actually instrumented.

When a new APK is built, preserve `com.asterion.writing.preview` package ID and user data; bump versionCode to enable in-place updates. Model files reside in app-private storage. Do not automatically uninstall or clear previous installs.

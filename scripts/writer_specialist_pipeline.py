#!/usr/bin/env python3
"""Provenance-gated training pipeline for a distinct Asterion Writer adapter.

Validation uses Python stdlib; training requires an explicitly provided GPU machine
and opt-in ML dependencies. No private EPUBs or model weights belong in this repo.
"""
import argparse
import hashlib
import json
import random
import re
from collections import Counter
from pathlib import Path

FIELDS = ("universe_id", "source_id", "license_id", "brief", "prose")
ALLOWED_LICENSES = {"CC0-1.0", "CC-BY-4.0", "MIT", "Apache-2.0", "original-owned"}


def sha(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def read_corpus(path):
    rows = []
    for number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"line {number}: invalid JSON: {exc}") from exc
        missing = [key for key in FIELDS if not isinstance(item.get(key), str) or not item[key].strip()]
        if missing:
            raise ValueError(f"line {number}: missing {', '.join(missing)}")
        if item["license_id"] not in ALLOWED_LICENSES:
            raise ValueError(f"line {number}: unverified training license {item['license_id']}")
        if len(item["prose"].split()) < 120:
            raise ValueError(f"line {number}: short or unsuitable narrative segment")
        if len(item["brief"]) > 30000 or len(item["prose"]) > 40000:
            raise ValueError(f"line {number}: oversized document")
        if item.get("quality", "approved") != "approved":
            continue
        rows.append(item)
    if not rows:
        raise ValueError("No provenance-verified approved writing samples")
    return rows


def partition(rows, seed=42, eval_fraction=0.2):
    universe_ids = sorted({row["universe_id"] for row in rows})
    if len(universe_ids) < 2:
        raise ValueError("At least two independent universes are required to prevent story leakage")
    rng = random.Random(seed)
    rng.shuffle(universe_ids)
    holdout_count = max(1, min(len(universe_ids) - 1, round(len(universe_ids) * eval_fraction)))
    held_out = set(universe_ids[:holdout_count])
    train = [row for row in rows if row["universe_id"] not in held_out]
    eval_rows = [row for row in rows if row["universe_id"] in held_out]
    # Exact passage duplicates and near-verbatim training/evaluation prefixes are forbidden.
    seen = {sha(re.sub(r"\\s+", " ", row["prose"]).strip().lower()) for row in train}
    for row in eval_rows:
        if sha(re.sub(r"\\s+", " ", row["prose"]).strip().lower()) in seen:
            raise ValueError("Training/evaluation contamination: duplicated prose")
    return train, eval_rows


def validate(args):
    rows = read_corpus(args.corpus)
    train, val = partition(rows, seed=args.seed)
    report = {
        "count": len(rows),
        "train": len(train),
        "held_out": len(val),
        "train_universes": len({row["universe_id"] for row in train}),
        "held_out_universes": len({row["universe_id"] for row in val}),
        "licenses": dict(Counter(row["license_id"] for row in rows)),
        "corpus_sha256": hashlib.sha256(Path(args.corpus).read_bytes()).hexdigest(),
        "trained": False,
    }
    print(json.dumps(report, indent=2))
    return train, val


def train(args):
    train_rows, eval_rows = validate(args)
    if not args.license_reviewed:
        raise ValueError("Training requires --license-reviewed after inspecting every source's actual rights")
    # Fail before loading a multi-billion parameter model on a device that has no viable training GPU.
    import torch
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU required for this training script; not supported on the current phone/PC")
    from datasets import Dataset
    from peft import LoraConfig, get_peft_model
    from transformers import AutoModelForCausalLM, AutoTokenizer, Trainer, TrainingArguments

    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    model = AutoModelForCausalLM.from_pretrained(
        args.model, torch_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16,
        trust_remote_code=False, device_map="auto")
    model.config.use_cache = False
    model.gradient_checkpointing_enable()
    lora = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05,
                      target_modules="all-linear", bias="none", task_type="CAUSAL_LM")
    model = get_peft_model(model, lora)

    def tokenize(row):
        prefix = ("Write original narrative prose for the following fiction brief. "
                  "Preserve character and causal continuity; no commentary.\n"
                  + row["brief"].strip() + "\n\nSTORY:\n")
        completion = row["prose"].strip() + tokenizer.eos_token
        prompt_ids = tokenizer(prefix, add_special_tokens=True, truncation=True,
                               max_length=args.max_tokens // 2)["input_ids"]
        target_ids = tokenizer(completion, add_special_tokens=False,
                               truncation=True, max_length=args.max_tokens - len(prompt_ids))["input_ids"]
        ids = prompt_ids + target_ids
        if not target_ids:
            raise ValueError("No target tokens remain after prompt")
        return {"input_ids": ids, "attention_mask": [1] * len(ids),
                "labels": [-100] * len(prompt_ids) + target_ids}

    def dataset(records):
        return Dataset.from_list([tokenize(item) for item in records])

    def collate(items):
        length = max(len(x["input_ids"]) for x in items)
        return {k: torch.tensor([
            x[k] + ([0] if k == "attention_mask" else
                    [-100] if k == "labels" else [tokenizer.pad_token_id]) * (length - len(x[k]))
            for x in items], dtype=torch.long)
            for k in ("input_ids", "attention_mask", "labels")}

    arguments = TrainingArguments(
        output_dir=str(output), per_device_train_batch_size=1,
        per_device_eval_batch_size=1, gradient_accumulation_steps=args.grad_accumulation,
        num_train_epochs=args.epochs, learning_rate=args.learning_rate,
        eval_strategy="epoch", save_strategy="epoch",
        bf16=torch.cuda.is_bf16_supported(), fp16=not torch.cuda.is_bf16_supported(),
        logging_steps=10, save_total_limit=2, report_to="none")
    trainer = Trainer(model=model, args=arguments, train_dataset=dataset(train_rows),
                      eval_dataset=dataset(eval_rows), data_collator=collate)
    trainer.train()
    metrics = trainer.evaluate()
    trainer.save_model(str(output / "adapter"))
    tokenizer.save_pretrained(str(output / "adapter"))
    (output / "training-receipt.json").write_text(json.dumps({
        "model_id": args.model,
        "corpus_sha256": hashlib.sha256(Path(args.corpus).read_bytes()).hexdigest(),
        "train_count": len(train_rows), "evaluation_count": len(eval_rows),
        "eval_metrics": metrics,
        "adapter_only": True, "gguf_converted": False, "phone_verified": False,
    }, indent=2, default=str), encoding="utf-8")
    print("Adapter trained. Next: untouched-base comparison, GGUF conversion, quantization, phone tests.")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["validate", "train"])
    p.add_argument("--corpus", required=True)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--model", help="Explicit verified base-model repo or local directory")
    p.add_argument("--output", default="./private-writer-adapter")
    p.add_argument("--max-tokens", type=int, default=2048)
    p.add_argument("--grad-accumulation", type=int, default=8)
    p.add_argument("--epochs", type=float, default=2.0)
    p.add_argument("--learning-rate", type=float, default=0.0001)
    p.add_argument("--license-reviewed", action="store_true")
    args = p.parse_args()
    if args.action == "train":
        if not args.model:
            p.error("--model is required for training")
        train(args)
    else:
        validate(args)


if __name__ == "__main__":
    main()

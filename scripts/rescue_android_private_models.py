#!/usr/bin/env python3
"""Lossless Asterion Writer GGUF rescue across Android debug package signatures.

Requires an already AUTHORIZED ADB connection and debuggable source/target apps.
This tool never uninstalls an app, changes Android security settings, downloads
weights, overwrites target files or deletes the source GGUF. Old v0.4 data is safe.

Examples:
  python scripts/rescue_android_private_models.py --list
  python scripts/rescue_android_private_models.py --backup-only
  python scripts/rescue_android_private_models.py --target com.asterion.writing.autonomous

The backup directory is private to the PC user and can contain several GiB.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

SAFE_NAME = re.compile(r"^[A-Za-z0-9_.-]{1,200}\.gguf$", re.IGNORECASE)
DEFAULT_SOURCE = "com.asterion.writing.bench"
DEFAULT_TARGET = "com.asterion.writing.autonomous"


def call(adb: str, *args: str, timeout: int = 30) -> str:
    p = subprocess.run([adb, *args], capture_output=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError(
            f"ADB command failed ({' '.join(args[:3])}): "
            + p.stderr.decode("utf-8", "replace").strip()[:400]
        )
    return p.stdout.decode("utf-8", "replace").strip()


def check_connection(adb: str) -> str:
    devices = call(adb, "devices", "-l").splitlines()[1:]
    ready = [line.split()[0] for line in devices if "\tdevice" in line]
    if len(ready) != 1:
        raise RuntimeError("Exactly one previously authorized ADB device required; found " + str(len(ready)))
    return ready[0]


def app_access(adb: str, package: str):
    if not re.fullmatch(r"[A-Za-z0-9_.]{5,150}", package):
        raise ValueError("Unsafe package ID")
    uid = call(adb, "shell", "run-as", package, "id")
    if "uid=" not in uid:
        raise RuntimeError("run-as denied for " + package + ": it must be an installed debuggable APK")
    return uid


def model_names(adb: str, source: str) -> list[str]:
    listing = call(adb, "shell", "run-as", source, "ls", "-1", "files/models")
    names = [name.strip() for name in listing.splitlines() if name.strip()]
    return sorted(name for name in names if SAFE_NAME.fullmatch(name))


def device_digest(adb: str, package: str, name: str) -> str:
    digest = call(adb, "shell", "run-as", package, "sha256sum", "files/models/" + name, timeout=600)
    value = digest.split()[0].lower()
    if not re.fullmatch(r"[a-f0-9]{64}", value):
        raise RuntimeError("Invalid device SHA-256 for model " + name)
    return value


def file_digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for piece in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(piece)
    return digest.hexdigest()


def device_exists(adb: str, package: str, filename: str) -> bool:
    result = subprocess.run([adb, "shell", "run-as", package, "test", "-f",
                             "files/models/" + filename], capture_output=True, timeout=15)
    return result.returncode == 0


def dump_model(adb: str, source: str, name: str, folder: Path, expected: str) -> Path:
    file = folder / name
    if file.exists():
        if file.is_symlink() or file_digest(file) != expected:
            raise RuntimeError(f"Unverified existing backup of {name}; refusing overwrite")
        print(json.dumps({"model": name, "backup": "already_verified", "sha256": expected}), flush=True)
        return file
    partial = folder / (name + ".rescue-part")
    if partial.exists():
        raise RuntimeError(f"Interrupted rescue exists: {partial}. Verify before retrying.")
    reader = subprocess.Popen(
        [adb, "exec-out", "run-as", source, "cat", "files/models/" + name],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if reader.stdout is None:
        raise RuntimeError("ADB data pipe unavailable")
    digest = hashlib.sha256()
    size = 0
    try:
        with partial.open("xb") as out:
            while chunk := reader.stdout.read(1024 * 1024):
                out.write(chunk)
                digest.update(chunk)
                size += len(chunk)
            out.flush()
            os.fsync(out.fileno())
        code = reader.wait(timeout=90)
        if code:
            raise RuntimeError(reader.stderr.read().decode("utf-8", "replace")[:400])
        if size < 4096 or digest.hexdigest() != expected:
            raise RuntimeError("Model backup SHA-256 does not match old Android app")
        partial.replace(file)
        print(json.dumps({"model": name, "backup": "verified", "bytes": size, "sha256": expected}), flush=True)
        return file
    except Exception:
        reader.kill()
        # Keep any partial rescue for inspection; never modify Android source.
        raise
    finally:
        if reader.poll() is None:
            reader.kill()
        if reader.stdout:
            reader.stdout.close()
        if reader.stderr:
            reader.stderr.close()


def import_model(adb: str, target: str, file: Path, expected: str):
    name = file.name
    call(adb, "shell", "run-as", target, "mkdir", "-p", "files/models")
    if device_exists(adb, target, name):
        if device_digest(adb, target, name) != expected:
            raise RuntimeError("Target contains a DIFFERENT model with this name; refusing overwrite")
        print(json.dumps({"model": name, "import": "already_verified"}), flush=True)
        return
    partial = name + ".rescue-part"
    if device_exists(adb, target, partial):
        raise RuntimeError("Previous target rescue-part exists; refusing overwrite")
    with file.open("rb") as source:
        writer = subprocess.Popen(
            [adb, "exec-in", "run-as", target, "dd", "of=files/models/" + partial, "bs=1048576"],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
        )
        if writer.stdin is None:
            raise RuntimeError("ADB import pipe unavailable")
        try:
            while chunk := source.read(1024 * 1024):
                writer.stdin.write(chunk)
            writer.stdin.close()
            if writer.wait(timeout=120):
                raise RuntimeError("Device import failed: " +
                    writer.stderr.read().decode("utf-8", "replace")[:400])
        finally:
            if writer.poll() is None:
                writer.kill()
            if writer.stderr:
                writer.stderr.close()
    # Verify before finalization. Original package stays untouched.
    if device_digest(adb, target, partial) != expected:
        raise RuntimeError("Android target model hash mismatch. Do not use partial model.")
    call(adb, "shell", "run-as", target, "mv", "files/models/" + partial, "files/models/" + name)
    if device_digest(adb, target, name) != expected:
        raise RuntimeError("Post-rename SHA-256 verification failed")
    print(json.dumps({"model": name, "import": "verified", "sha256": expected}), flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    p.add_argument("--source", default=DEFAULT_SOURCE)
    p.add_argument("--target", default=DEFAULT_TARGET)
    p.add_argument("--backup-dir", type=Path, default=Path.home() / "Downloads" / "AsterionWriterModelRescue")
    p.add_argument("--list", action="store_true")
    p.add_argument("--backup-only", action="store_true")
    p.add_argument("--restore-only", action="store_true", help="Restore verified backups after a signature-changing same-package reinstall")
    args = p.parse_args()

    check_connection(args.adb)
    if args.backup_only and args.restore_only:
        raise ValueError("--backup-only and --restore-only cannot be combined")
    if args.restore_only:
        app_access(args.adb, args.target)
        if not args.backup_dir.is_dir():
            raise RuntimeError("Verified model backup directory is missing; no import attempted")
        backups = sorted(p for p in args.backup_dir.iterdir()
                         if p.is_file() and not p.is_symlink() and SAFE_NAME.fullmatch(p.name))
        if not backups:
            raise RuntimeError("No verified GGUF backup files were found")
        for file in backups:
            digest = file_digest(file)
            if file.stat().st_size < 4096:
                raise RuntimeError("A backup is too small to be a real GGUF")
            print(json.dumps({"model": file.name, "restore_from_backup_sha256": digest}), flush=True)
            import_model(args.adb, args.target, file, digest)
        print("VERIFIED_MODEL_RESTORE_COMPLETE; BACKUPS_PRESERVED", flush=True)
        return
    app_access(args.adb, args.source)
    names = model_names(args.adb, args.source)
    if not names:
        raise RuntimeError("No completed GGUF models found in source package; no files modified.")
    print(json.dumps({"source": args.source, "models": names}), flush=True)
    if args.list:
        return
    if not args.backup_only:
        app_access(args.adb, args.target)
    args.backup_dir.mkdir(parents=True, exist_ok=True)
    for name in names:
        digest = device_digest(args.adb, args.source, name)
        file = dump_model(args.adb, args.source, name, args.backup_dir, digest)
        if not args.backup_only:
            import_model(args.adb, args.target, file, digest)
    print("MODELS_PRESERVED_AND_VERIFIED; SOURCE_UNCHANGED", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("RESCUE_NOT_COMPLETED: " + str(error), file=sys.stderr, flush=True)
        sys.exit(1)

#!/usr/bin/env python3
"""Read-only, binary-safe backup of the installed debug Writer's private files.

Requires an authorized ADB connection and an installed debuggable Writer APK.
Does NOT uninstall, clear, modify, or update the phone application.
The archive remains LOCAL on the owner's Windows PC; never upload it to Git.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import time


def adb_check(adb: str, *args: str) -> str:
    run = subprocess.run([adb, *args], stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, timeout=20)
    if run.returncode:
        raise RuntimeError(f"ADB read-only check failed ({run.returncode}): "
                           + run.stderr.decode(errors="replace")[-500:])
    return run.stdout.decode("utf-8", errors="replace").strip()


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default="adb", help="Existing authorized adb executable")
    p.add_argument("--package", default="com.asterion.writing.bench")
    p.add_argument("--out", required=True, help="Private local backup.tar destination")
    args = p.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.]+", args.package):
        p.error("Unsafe package name")
    destination = Path(args.out).expanduser().resolve()
    if destination.exists():
        p.error("Destination already exists; refusing to overwrite existing backup")
    destination.parent.mkdir(parents=True, exist_ok=True)
    devices = adb_check(args.adb, "devices", "-l")
    connected = [line for line in devices.splitlines()[1:] if "\tdevice" in line]
    if len(connected) != 1:
        raise RuntimeError("Exactly one authorized ADB device required; no files changed")
    pwd = adb_check(args.adb, "shell", "run-as", args.package, "pwd")
    if args.package not in pwd:
        raise RuntimeError("run-as inaccessible. The installed app must be debuggable; "
                           "do not uninstall it as a workaround")
    names = set(adb_check(args.adb, "shell", "run-as", args.package, "ls", "-1").splitlines())
    folders = [name for name in ("files", "shared_prefs", "databases", "no_backup")
               if name in names]
    if "files" not in folders:
        raise RuntimeError("Installed app data does not expose files/; stopping")
    command = [args.adb, "exec-out", "run-as", args.package, "tar", "-cf", "-", *folders]
    temp = destination.with_name(destination.name + ".incomplete")
    if temp.exists():
        raise RuntimeError(f"Prior incomplete backup exists: {temp}; no replacement")
    total = 0
    digest = hashlib.sha256()
    try:
        with temp.open("xb") as target:
            task = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            assert task.stdout is not None
            while True:
                chunk = task.stdout.read(1024 * 1024)
                if not chunk:
                    break
                target.write(chunk)
                digest.update(chunk)
                total += len(chunk)
            stderr = task.stderr.read().decode(errors="replace") if task.stderr else ""
            status = task.wait(timeout=30)
            target.flush()
            os.fsync(target.fileno())
        if status != 0:
            raise RuntimeError(f"run-as archive export failed ({status}): {stderr[-500:]}")
        found = []
        with tarfile.open(temp, "r:") as tar:
            for entry in tar:
                path = entry.name.lstrip("./")
                if (entry.name.startswith("/") or ".." in Path(path).parts or entry.issym()
                        or entry.islnk()):
                    raise RuntimeError("Unsafe archive member detected; stopping")
                if path.endswith(".gguf") or path.endswith(".gguf.part"):
                    found.append({"path": path, "bytes": entry.size})
        if not found:
            raise RuntimeError("No local GGUF model found in verified archive; "
                               "leave the installed app unchanged")
        temp.rename(destination)
        receipt = {
            "result": "BACKUP_VERIFIED_ONLY", "archive": str(destination),
            "bytes": total, "sha256": digest.hexdigest(), "models": found,
            "package": args.package, "installed_app_unchanged": True,
            "safe_to_uninstall": False,
            "action_required": "Verify recovery and obtain explicit owner approval "
                               "before any app replacement.",
            "timestamp_epoch": int(time.time())
        }
        destination.with_suffix(destination.suffix + ".receipt.json").write_text(
            json.dumps(receipt, indent=2), encoding="utf-8")
        print(json.dumps(receipt, indent=2))
    except BaseException:
        # Keep any unfinished backup for diagnostics, never overwrite it implicitly.
        raise


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Configurable, sharded and cache-aware HYDRA test orchestrator."""

from __future__ import annotations
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config" / "hydra-test-profiles.json"


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def digest(paths: list[Path], extra: str) -> str:
    checksum = hashlib.sha256(extra.encode("utf-8"))
    for path in sorted({item.resolve() for item in paths if item.exists()}):
        if path.is_file():
            checksum.update(str(path.relative_to(ROOT)).encode("utf-8"))
            checksum.update(path.read_bytes())
        elif path.is_dir():
            for child in sorted(item for item in path.rglob("*") if item.is_file()
                                and ".hydra-test-cache" not in item.parts
                                and "node_modules" not in item.parts
                                and "target" not in item.parts):
                checksum.update(str(child.relative_to(ROOT)).encode("utf-8"))
                checksum.update(child.read_bytes())
    return checksum.hexdigest()


def changed_paths(args: argparse.Namespace) -> list[str]:
    if args.changed_file:
        return [line.strip().replace("\\", "/") for line in Path(args.changed_file).read_text(
            encoding="utf-8").splitlines() if line.strip()]
    if args.changed:
        return [value.replace("\\", "/") for value in args.changed]
    return []


def matches_any(path: str, roots: list[str]) -> bool:
    return any(path == root or path.startswith(root.rstrip("/") + "/") for root in roots)


def select_tasks(config: dict[str, Any], profile: dict[str, Any],
                 changed: list[str]) -> list[tuple[str, list[str], Path]]:
    selection = config["selection"]
    backend = bool(profile["runBackend"])
    frontend = bool(profile["runFrontend"])
    if changed:
        shared = any(matches_any(path, selection["sharedRoots"]) for path in changed)
        backend = backend and (shared or any(matches_any(path, selection["backendRoots"]) for path in changed))
        frontend = frontend and (shared or any(matches_any(path, selection["frontendRoots"]) for path in changed))
    tasks: list[tuple[str, list[str], Path]] = []
    if backend and profile["backend"]:
        command = list(profile["backend"])
        if os.name == "nt" and command[0] == "./mvnw":
            command[0] = "mvnw.cmd"
        tasks.append(("backend", command, ROOT))
    if frontend and profile["frontend"]:
        command = list(profile["frontend"])
        if os.name == "nt" and command[0] == "npm":
            command[0] = "npm.cmd"
        tasks.append(("frontend", command, ROOT / "frontend"))
    return tasks


def shard(tasks: list[tuple[str, list[str], Path]], index: int, count: int) -> list[tuple[str, list[str], Path]]:
    """Simple deterministic task sharding retained for external callers and tests."""
    if count <= 1:
        return tasks
    if index < 0 or index >= count:
        raise ValueError("shard-index must be between 0 and shard-count - 1")
    return [task for position, task in enumerate(tasks) if position % count == index]


def backend_test_classes(index: int, count: int) -> list[str]:
    tests = sorted((ROOT / "src" / "test" / "java").rglob("*.java"))
    classes: list[str] = []
    for path in tests:
        relative = path.relative_to(ROOT / "src" / "test" / "java")
        class_name = ".".join(relative.with_suffix("").parts)
        bucket = int(hashlib.sha256(class_name.encode("utf-8")).hexdigest()[:8], 16) % count
        if bucket == index:
            classes.append(class_name)
    return classes


def shard_suites(tasks: list[tuple[str, list[str], Path]], index: int,
                 count: int) -> list[tuple[str, list[str], Path]]:
    if count <= 1:
        return tasks
    if index < 0 or index >= count:
        raise ValueError("shard-index must be between 0 and shard-count - 1")
    result: list[tuple[str, list[str], Path]] = []
    for name, command, cwd in tasks:
        command = list(command)
        if name == "frontend":
            command.extend(["--", f"--shard={index + 1}/{count}"])
            result.append((name, command, cwd))
        elif name == "backend":
            classes = backend_test_classes(index, count)
            if classes:
                shard_root = ROOT / ".hydra-test-cache" / "shards"
                shard_root.mkdir(parents=True, exist_ok=True)
                include_file = shard_root / f"backend-{count}-{index}.txt"
                include_file.write_text("\n".join(
                    "**/" + class_name.rsplit(".", 1)[-1] + ".java" for class_name in classes
                ) + "\n", encoding="utf-8")
                command.append("-Dsurefire.includesFile=" + str(include_file))
                result.append((name, command, cwd))
    return result


def run_task(task: tuple[str, list[str], Path], profile_name: str,
             config: dict[str, Any], profile: dict[str, Any],
             use_cache: bool, dry_run: bool) -> int:
    name, command, cwd = task
    limits = config["limits"]
    cache_root = ROOT / limits["cacheDirectory"]
    log_root = ROOT / limits["logDirectory"]
    cache_root.mkdir(parents=True, exist_ok=True)
    log_root.mkdir(parents=True, exist_ok=True)
    source_paths = [ROOT / value for value in config["selection"][f"{name}Roots"]]
    key = digest(source_paths + [CONFIG_PATH], profile_name + json.dumps(command))
    marker = cache_root / f"{profile_name}-{name}-{key}.passed"
    if use_cache and marker.exists():
        print(f"[CACHE] {name}: {key[:12]}")
        return 0
    print(f"[RUN] {name}: {' '.join(command)}")
    if dry_run:
        return 0
    attempts = max(1, int(profile.get("retries", 0)) + 1)
    timeout = max(1, int(profile.get("taskTimeoutSeconds", 3600)))
    log = log_root / f"{profile_name}-{name}.log"
    for attempt in range(1, attempts + 1):
        started = time.time()
        try:
            result = subprocess.run(command, cwd=cwd, text=True, encoding="utf-8", errors="replace",
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False,
                                    timeout=timeout)
            output = result.stdout
            code = result.returncode
        except subprocess.TimeoutExpired as exception:
            output = (exception.stdout or "") + f"\n[TIMEOUT] exceeded {timeout} seconds\n"
            code = 124
        log.write_text(output, encoding="utf-8")
        print(output)
        print(f"[DONE] {name}: exit={code}, attempt={attempt}/{attempts}, "
              f"duration={time.time() - started:.2f}s, log={log}")
        if code == 0:
            if use_cache:
                marker.write_text("passed\n", encoding="utf-8")
            return 0
        if attempt < attempts:
            print(f"[RETRY] {name}: retrying because the selected profile explicitly allows it")
    return code


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", default="fast")
    parser.add_argument("--changed", action="append", default=[])
    parser.add_argument("--changed-file")
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("--shard-count", type=int, default=1)
    parser.add_argument("--no-cache", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    config = load_config()
    profile = config["profiles"].get(args.profile)
    if profile is None:
        raise SystemExit(f"Unknown profile: {args.profile}")
    tasks = shard_suites(select_tasks(config, profile, changed_paths(args)),
                         args.shard_index, args.shard_count)
    if not tasks:
        print("[SKIP] No test task matches the selected profile and changed paths.")
        return 0
    use_cache = bool(profile["cache"]) and not args.no_cache
    if profile["parallelTasks"] and len(tasks) > 1:
        workers = min(len(tasks), int(config["limits"]["maximumParallelTasks"]))
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
            results = list(executor.map(lambda task: run_task(
                task, args.profile, config, profile, use_cache, args.dry_run), tasks))
    else:
        results = [run_task(task, args.profile, config, profile, use_cache, args.dry_run) for task in tasks]
    return max(results, default=0)


if __name__ == "__main__":
    raise SystemExit(main())

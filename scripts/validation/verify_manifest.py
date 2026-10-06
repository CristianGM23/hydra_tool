#!/usr/bin/env python3
from __future__ import annotations

"""Verifica que MANIFEST_SHA256.txt describa exactamente el inventario fuente publicable."""

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts" / "release"))
from source_inventory import source_inventory  # noqa: E402

MANIFEST = ROOT / "MANIFEST_SHA256.txt"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    if not MANIFEST.is_file():
        print("FAIL missing MANIFEST_SHA256.txt")
        return 1
    version = json.loads((ROOT / "config/hydra-version.json").read_text(encoding="utf-8"))["version"]
    actual = source_inventory(ROOT, version)
    actual.pop("MANIFEST_SHA256.txt", None)
    expected: dict[str, str] = {}
    malformed = False
    for line_number, line in enumerate(MANIFEST.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        if "  " not in line:
            print(f"FAIL malformed manifest line {line_number}")
            malformed = True
            continue
        digest, relative = line.split("  ", 1)
        if len(digest) != 64 or any(character not in "0123456789abcdef" for character in digest.lower()):
            print(f"FAIL invalid digest on manifest line {line_number}")
            malformed = True
            continue
        if relative in expected:
            print(f"FAIL duplicate manifest entry {relative}")
            malformed = True
            continue
        expected[relative] = digest.lower()
    if malformed:
        return 1
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    changed = sorted(relative for relative, path in actual.items() if expected.get(relative) not in (None, sha256(path)))
    if missing or extra or changed:
        for relative in missing:
            print(f"FAIL missing {relative}")
        for relative in extra:
            print(f"FAIL unlisted {relative}")
        for relative in changed:
            print(f"FAIL changed {relative}")
        return 1
    print(f"Manifest verified: {len(expected)}/{len(actual)} canonical source files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

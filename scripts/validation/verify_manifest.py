#!/usr/bin/env python3
from __future__ import annotations

"""Verifica hashes canónicos y, opcionalmente, tolera extras preservados del workspace."""

import argparse
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-extra",
        action="store_true",
        help=(
            "Verifica presencia y hash de todas las rutas canónicas, pero permite "
            "rutas fuente adicionales del workspace que no figuren en el manifiesto."
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
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
    # Unlisted workspace files are not part of the signed manifest. Never read
    # their contents: generators such as Archify can remove temporary files
    # between source discovery and verification.
    changed: list[str] = []
    unreadable: list[str] = []
    for relative in sorted(set(expected) & set(actual)):
        try:
            if sha256(actual[relative]) != expected[relative]:
                changed.append(relative)
        except OSError:
            unreadable.append(relative)
    blocking_extra = [] if args.allow_extra else extra
    if missing or blocking_extra or changed or unreadable:
        for relative in missing:
            print(f"FAIL missing {relative}")
        for relative in blocking_extra:
            print(f"FAIL unlisted {relative}")
        for relative in changed:
            print(f"FAIL changed {relative}")
        for relative in unreadable:
            print(f"FAIL unreadable canonical source {relative}")
        return 1
    if args.allow_extra:
        print(
            f"Manifest verified: {len(expected)}/{len(expected)} canonical source files; "
            f"preserved workspace extras={len(extra)}"
        )
        if extra:
            preview = extra[:12]
            for relative in preview:
                print(f"PRESERVE extra {relative}")
            if len(extra) > len(preview):
                print(f"PRESERVE ... {len(extra) - len(preview)} additional workspace extras")
    else:
        print(f"Manifest verified: {len(expected)}/{len(actual)} canonical source files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
